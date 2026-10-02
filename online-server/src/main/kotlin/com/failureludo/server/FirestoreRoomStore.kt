package com.failureludo.server

import com.failureludo.online.*

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** All authoritative writes occur together; no process-local room cache or locks. */
class FirestoreRoomStore(
    private val db: Firestore, private val nowMillis: () -> Long = System::currentTimeMillis
) : RoomStore {
    override fun get(code: String): OnlineRoom? = firestoreCall {
        decode(db.collection(ROOMS).document(code).get().get(15, TimeUnit.SECONDS))
    }

    override fun execute(
        requestKey: String, fingerprint: String, code: String, change: (OnlineRoom?) -> OnlineRoom
    ): CommandResult = firestoreCall {
        db.runTransaction { tx ->
            val receiptRef = db.collection(REQUESTS).document(requestKey)
            val receipt = tx.get(receiptRef).get()
            if (receipt.exists()) {
                if (receipt.getString("fingerprint") != fingerprint) {
                    reject(409, "REQUEST_ID_REUSED", "Use a new request ID for a different command.")
                }
                val roomRef = db.collection(ROOMS).document(requireNotNull(receipt.getString("roomCode")))
                val room = decode(tx.get(roomRef).get())
                    ?: reject(410, "ROOM_EXPIRED", "This room is no longer available.")
                CommandResult(room, requireNotNull(receipt.getLong("acceptedRevision")), true)
            } else {
                val ref = db.collection(ROOMS).document(code)
                val previousDoc = tx.get(ref).get()
                val previous = decode(previousDoc)
                val next = change(previous)
                check(next.code == code)
                val now = nowMillis()
                // Receipts are checked first: recovering a create never consumes quota.
                val createQuota = if (previous == null) quotaRef(db, "create", next.hostUid) else null
                val quota = createQuota?.let { RateWindow.consume(tx.get(it).get(), now, 10, 3_600_000) }
                val fields = roomFields(next).toMutableMap()
                fields["createdAtMillis"] = previousDoc.getLong("createdAtMillis") ?: now
                previousDoc.getLong("closedAtMillis")?.let { fields["closedAtMillis"] = it }
                val activity = if (previous == null || previous.revision != next.revision) now
                    else previousDoc.getLong("lastActivityAtMillis") ?: now
                fields["lastActivityAtMillis"] = activity
                if (next.status == RoomStatus.WAITING || next.status == RoomStatus.PLAYING) {
                    fields["cleanupAtMillis"] = activity + IDLE_ROOM_MILLIS
                }
                if (createQuota != null) tx.set(createQuota, requireNotNull(quota))
                tx.set(ref, fields)
                tx.create(receiptRef, mapOf("fingerprint" to fingerprint, "roomCode" to code,
                    "acceptedRevision" to next.revision))
                CommandResult(next, next.revision, false)
            }
        }.get(30, TimeUnit.SECONDS)
    }

    private fun decode(doc: DocumentSnapshot): OnlineRoom? =
        if (!doc.exists()) null else RoomCodec.decode(JSONObject(requireNotNull(doc.getString("snapshot"))))

    /** Bounded maintenance pass. Retain closed snapshots and all receipts for recovery/history. */
    fun cleanupExpiredRooms(apply: Boolean = false, limit: Int = 100): CleanupResult = firestoreCall {
        require(limit in 1..500)
        val cutoff = nowMillis()
        val candidates = db.collection(ROOMS).whereLessThanOrEqualTo("cleanupAtMillis", cutoff)
            .orderBy("cleanupAtMillis").limit(limit).get().get(15, TimeUnit.SECONDS).documents
        val closed = if (apply) candidates.count { closeIfExpired(it.id, cutoff) } else 0
        CleanupResult(candidates.size, closed)
    }

    internal fun closeIfExpired(code: String, cutoff: Long): Boolean = firestoreCall {
        db.runTransaction { tx ->
            val ref = db.collection(ROOMS).document(code)
            val doc = tx.get(ref).get()
            val due = doc.getLong("cleanupAtMillis")
            val room = decode(doc)
            if (due == null || due > cutoff || room == null ||
                room.status !in listOf(RoomStatus.WAITING, RoomStatus.PLAYING)) return@runTransaction false
            val closed = room.copy(status = RoomStatus.CLOSED, revision = room.revision + 1,
                actionDeadlineAtMillis = null, lastAction = LastAction("EXPIRED", room.hostUid))
            // Recheck in the transaction: a player action after scanning wins safely.
            tx.update(ref, roomFields(closed) + mapOf(
                "cleanupAtMillis" to com.google.cloud.firestore.FieldValue.delete(),
                "closedAtMillis" to nowMillis()))
            true
        }.get(30, TimeUnit.SECONDS)
    }

    private fun roomFields(room: OnlineRoom): Map<String, Any> = mapOf(
        "snapshot" to RoomCodec.encode(room).toString(), "memberUids" to room.members.map { it.uid },
        "revision" to room.revision)

    companion object {
        const val IDLE_ROOM_MILLIS = 24 * 60 * 60 * 1000L
        const val ROOMS = "authoritativeRooms"
        const val REQUESTS = "authoritativeRequests"
    }
}

data class CleanupResult(val candidates: Int, val closed: Int)

internal fun <T> firestoreCall(action: () -> T): T = try {
    action()
} catch (error: ExecutionException) {
    val causes = generateSequence<Throwable>(error) { it.cause }.toList()
    causes.filterIsInstance<ApiException>().firstOrNull()?.let { throw it }
    causes.filterIsInstance<UnsupportedRoomVersion>().firstOrNull()?.let { throw it }
    throw ApiException(503, "STORE_UNAVAILABLE", "Storage unavailable. Retry with the same request ID.", error)
} catch (error: TimeoutException) {
    // A timed-out write may still commit. Its receipt makes a retry safe.
    throw ApiException(503, "STORE_TIMEOUT", "Storage timed out. Retry with the same request ID.", error)
} catch (error: InterruptedException) {
    Thread.currentThread().interrupt()
    throw ApiException(503, "STORE_INTERRUPTED", "Request interrupted. Retry with the same request ID.", error)
}
