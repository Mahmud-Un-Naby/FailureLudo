package com.failureludo.data.online

import android.content.Context
import android.util.AtomicFile
import com.failureludo.BuildConfig
import com.failureludo.engine.GameMode
import com.failureludo.online.*
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale

// Confirmed snapshots only. connected=false prevents acting on cached/offline data.
data class OnlineSessionState(
    val room: OnlineRoom? = null, val uid: String? = null, val busy: Boolean = false,
    val pending: Boolean = false, val connected: Boolean = false, val error: String? = null,
    val configured: Boolean = BuildConfig.ONLINE_API_URL.isNotBlank(),
    val actionRemainingMillis: Long? = null,
    val afkRemainingMillis: Long? = null,
    val deadlineDue: Boolean = false
) {
    val canAct: Boolean get() = configured && connected && !busy && !pending && error == null
    val canPlay: Boolean get() = canAct && !deadlineDue && (room?.actionDeadlineAtMillis == null ||
        actionRemainingMillis?.let { it > 0 } == true)
}

class OnlineGameRepository private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutex = Mutex()
    private val deadlineClock = OnlineDeadlineClock(android.os.SystemClock::elapsedRealtime)
    private val api = OnlineApi(BuildConfig.ONLINE_API_URL, deadlineClock::observe)
    private val file = AtomicFile(File(context.noBackupFilesDir, "online-session-v1.json"))
    private var journal: OnlineCommandJournal? = null
    private val mutable = MutableStateFlow(OnlineSessionState())
    val state: StateFlow<OnlineSessionState> = mutable.asStateFlow()
    private var watcher: Job? = null
    private var watchedCode: String? = null
    private var clients = 0
    private var deadlineWatcher: Job? = null

    private fun watchDeadline() {
        if (deadlineWatcher?.isActive == true) return
        deadlineWatcher = scope.launch {
            var nextCheck = 0L
            var observed: Pair<String, Long>? = null
            while (isActive) {
                val room = mutable.value.room
                val identity = room?.let { it.code to it.revision }
                if (identity != observed) { observed = identity; nextCheck = 0L }
                updateTimers()
                val remaining = deadlineClock.remainingMillis(room?.nextDeadlineAtMillis())
                val elapsed = android.os.SystemClock.elapsedRealtime()
                if (room?.status == RoomStatus.PLAYING && room.nextDeadlineAtMillis() != null &&
                    (remaining == null || remaining == 0L) && !mutable.value.busy && elapsed >= nextCheck) {
                    nextCheck = elapsed + 15_000
                    checkDeadline()
                }
                delay(1000)
            }
        }
    }

    private fun updateTimers() {
        mutable.update { session ->
            val room = session.room
            val member = room?.members?.find { it.uid == session.uid }
            val afkDeadline = member?.afkSinceMillis?.let { start -> room.afkTimeoutMillis?.let { start + it } }
            session.copy(actionRemainingMillis = deadlineClock.remainingMillis(room?.actionDeadlineAtMillis),
                afkRemainingMillis = if (room?.status == RoomStatus.PLAYING) deadlineClock.remainingMillis(afkDeadline) else null,
                deadlineDue = deadlineClock.remainingMillis(room?.nextDeadlineAtMillis()) == 0L)
        }
    }

    private fun checkDeadline() = launchOperation {
        // Recover the original intention first. A lost response must never become
        // a new timeout intention while that original receipt remains unresolved.
        if (journal!!.state!!.pending != null) sendPending()
        val room = journal!!.state!!.room ?: return@launchOperation
        if (room.status != RoomStatus.PLAYING || room.actionDeadlineAtMillis == null) return@launchOperation
        if (deadlineClock.remainingMillis(room.nextDeadlineAtMillis()) == null) {
            refresh(room.code)
            return@launchOperation
        }
        if (deadlineClock.remainingMillis(room.nextDeadlineAtMillis()) != 0L) return@launchOperation
        try {
            send("/v1/rooms/${room.code}/commands", JSONObject().put("type", "CHECK_TIMEOUT")
                .put("expectedRevision", room.revision))
        } catch (error: OnlineApiException) {
            // Another device may have resolved the deadline first, or clock/network
            // error may have made this check early. sendPending refreshes these cases.
            if (error.code !in listOf("TIME_REMAINING", "STALE_REVISION", "NOT_PLAYING")) throw error
        }
    }

    fun attach() { clients++; if (clients == 1) { watchDeadline(); retry() } }
    fun detach() {
        clients = (clients - 1).coerceAtLeast(0)
        if (clients == 0) {
            watcher?.cancel(); watcher = null; watchedCode = null
            deadlineWatcher?.cancel(); deadlineWatcher = null
            mutable.update { it.copy(connected = false) }
        }
    }

    fun retry() = launchOperation {
        if (journal!!.state!!.pending != null) sendPending()
        journal!!.state!!.room?.let { refresh(it.code) }
    }

    fun create(maxPlayers: Int, mode: GameMode) = launchOperation {
        check(journal!!.state!!.room == null) { "Resume or leave your current room first." }
        val uid = requireNotNull(mutable.value.uid)
        send("/v1/rooms", JSONObject().put("name", "Guest#${uid.takeLast(4).uppercase(Locale.ROOT)}")
            .put("maxPlayers", maxPlayers).put("mode", mode.name))
    }

    fun join(rawCode: String) = launchOperation {
        check(journal!!.state!!.room == null) { "Resume or leave your current room first." }
        val code = rawCode.trim().uppercase(Locale.ROOT)
        require(code.matches(Regex("[A-HJ-NP-Z2-9]{8}"))) { "Enter the eight-character room code." }
        val uid = requireNotNull(mutable.value.uid)
        send("/v1/rooms/$code/commands", JSONObject().put("type", "JOIN")
            .put("name", "Guest#${uid.takeLast(4).uppercase(Locale.ROOT)}"))
    }

    fun command(type: String, fields: JSONObject = JSONObject()) {
        val clicked = state.value
        val room = clicked.room ?: return
        if (!clicked.canAct || (type in listOf("ROLL", "MOVE") && !clicked.canPlay)) return
        // Freeze the revision that the player actually saw. A listener may advance
        // the journal while identity/disk work suspends; never retarget that tap.
        val body = JSONObject(fields.toString()).put("type", type).put("expectedRevision", room.revision)
        launchOperation {
            check(journal!!.state!!.room?.code == room.code) { "Your room changed. Open it again." }
            send("/v1/rooms/${room.code}/commands", body)
        }
    }

    fun forgetRoom() = launchOperation {
        check(journal!!.state!!.room?.canForget(mutable.value.uid) == true)
        journal!!.forgetRoom()
        watcher?.cancel(); watcher = null; watchedCode = null
    }

    private fun launchOperation(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        scope.launch {
            mutex.withLock {
                try {
                    check(mutable.value.configured) { "Online play is not available in this build yet. Offline games are ready to play." }
                    ensureIdentity()
                    block()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    mutable.update { it.copy(error = error.message ?: "Could not connect. Please retry.", connected = false) }
                } finally {
                    publish()
                    mutable.update { it.copy(busy = false) }
                    ensureWatcher()
                }
            }
        }
    }

    private suspend fun ensureIdentity() {
        // Firebase is first accessed from an online destination, never offline startup.
        val auth = Firebase.auth
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
            ?: error("Could not start a guest session. Please retry.")
        if (journal == null) journal = withContext(Dispatchers.IO) {
            OnlineCommandJournal(object : JournalStorage {
                override fun read(): String? = try { file.openRead().bufferedReader().use { it.readText() } }
                    catch (_: FileNotFoundException) { null }
                override fun write(value: String) {
                    val output = file.startWrite()
                    try {
                        output.write(value.toByteArray(Charsets.UTF_8))
                        output.fd.sync()
                        file.finishWrite(output)
                        check(file.openRead().bufferedReader().use { it.readText() } == value) {
                            "Could not save the online action. Free some storage and retry."
                        }
                    }
                    catch (error: Exception) { file.failWrite(output); throw error }
                }
            })
        }
        withContext(Dispatchers.IO) { journal!!.bind(user.uid, BuildConfig.ONLINE_API_URL) }
        mutable.update { it.copy(uid = user.uid) }
        publish()
    }

    private suspend fun request(path: String, body: String? = null): JSONObject {
        val user = Firebase.auth.currentUser ?: error("Your guest session is unavailable. Please retry.")
        check(user.uid == journal!!.state!!.uid) { "Your identity changed. Restore the original session." }
        suspend fun token(force: Boolean) = user.getIdToken(force).await().token ?: error("Could not refresh your session.")
        return try { api.request(path, token(false), body) }
        catch (error: OnlineApiException) {
            if (error.status != 401) throw error
            api.request(path, token(true), body)
        }
    }

    private suspend fun send(path: String, body: JSONObject) {
        withContext(Dispatchers.IO) { journal!!.begin(path, body) }
        publish()
        sendPending()
    }

    private suspend fun sendPending() {
        try {
            withContext(Dispatchers.IO) {
                deliverPending(journal!!) { pending -> request(pending.path, pending.body) }
            }
            mutable.update { it.copy(connected = true, error = null) }
        } catch (error: OnlineApiException) {
            if (error.code in listOf("STALE_REVISION", "ALREADY_STARTED", "TURN_EXPIRED", "TIME_REMAINING", "NOT_PLAYING", "AFK_EXPIRED", "ALREADY_ACTIVE")) {
                journal!!.state!!.room?.let { refresh(it.code) }
            }
            throw error
        }
        publish()
    }

    private suspend fun refresh(code: String) {
        try {
            val room = RoomCodec.decode(request("/v1/rooms/$code").getJSONObject("room"))
            withContext(Dispatchers.IO) { journal!!.accept(room) }
            mutable.update { it.copy(connected = true, error = null) }
            publish()
        } catch (error: OnlineApiException) {
            if (error.code in listOf("NOT_A_MEMBER", "ROOM_NOT_FOUND", "ROOM_EXPIRED") && journal!!.state!!.pending == null) {
                withContext(Dispatchers.IO) { journal!!.completeExit() }
                publish()
            }
            throw error
        }
    }

    private fun publish() {
        val saved = journal?.state ?: return
        mutable.update { it.copy(room = saved.room, pending = saved.pending != null) }
        updateTimers()
    }

    private fun ensureWatcher() {
        val code = journal?.state?.room?.code
        if (clients == 0 || code == null) {
            watcher?.cancel(); watcher = null; watchedCode = null
            return
        }
        if (watchedCode == code && watcher?.isActive == true) return
        watcher?.cancel(); watchedCode = code
        watcher = scope.launch {
            while (isActive) {
                try {
                    snapshots(code).collect { (room, fromCache) ->
                        mutex.withLock {
                            if (journal?.state?.room?.code != code) return@withLock
                            if (fromCache) mutable.update { it.copy(connected = false) }
                            else {
                                withContext(Dispatchers.IO) { journal!!.accept(room) }
                                mutable.update { it.copy(connected = true,
                                    error = if (it.error == "Live updates disconnected. Reconnecting…") null else it.error) }
                                publish()
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    mutable.update { it.copy(connected = false, error = "Live updates disconnected. Reconnecting…") }
                    delay(3000)
                    mutex.withLock {
                        try {
                            if (journal?.state?.room?.code == code) refresh(code)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Keep the last confirmed board and retry the listener. */ }
                    }
                    if (journal?.state?.room?.code != code) return@launch
                }
            }
        }
    }

    private fun snapshots(code: String) = callbackFlow {
        val registration = Firebase.firestore.collection("authoritativeRooms").document(code)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                if (snapshot == null || !snapshot.exists()) { close(IllegalStateException("Room unavailable")); return@addSnapshotListener }
                try {
                    val room = RoomCodec.decode(JSONObject(requireNotNull(snapshot.getString("snapshot"))))
                    trySend(room to snapshot.metadata.isFromCache)
                } catch (error: Exception) { close(error) }
            }
        awaitClose { registration.remove() }
    }

    companion object {
        @Volatile private var instance: OnlineGameRepository? = null
        fun get(context: Context): OnlineGameRepository = instance ?: synchronized(this) {
            instance ?: OnlineGameRepository(context.applicationContext).also { instance = it }
        }
    }
}
