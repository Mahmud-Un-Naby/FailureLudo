package com.failureludo.server

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import org.json.JSONObject
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** All authoritative writes occur together; no process-local room cache or locks. */
class FirestoreRoomStore(private val db: Firestore) : RoomStore {
    override fun get(code: String): OnlineRoom? = await {
        decode(db.collection(ROOMS).document(code).get().get(15, TimeUnit.SECONDS))
    }

    override fun execute(
        requestKey: String, fingerprint: String, code: String, change: (OnlineRoom?) -> OnlineRoom
    ): CommandResult = await {
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
                val next = change(decode(tx.get(ref).get()))
                check(next.code == code)
                tx.set(ref, mapOf("snapshot" to RoomCodec.encode(next).toString(),
                    "memberUids" to next.members.map { it.uid }, "revision" to next.revision))
                tx.create(receiptRef, mapOf("fingerprint" to fingerprint, "roomCode" to code,
                    "acceptedRevision" to next.revision))
                CommandResult(next, next.revision, false)
            }
        }.get(30, TimeUnit.SECONDS)
    }

    private fun decode(doc: DocumentSnapshot): OnlineRoom? =
        if (!doc.exists()) null else RoomCodec.decode(JSONObject(requireNotNull(doc.getString("snapshot"))))

    private fun <T> await(action: () -> T): T = try {
        action()
    } catch (error: ExecutionException) {
        val causes = generateSequence<Throwable>(error) { it.cause }.toList()
        causes.filterIsInstance<ApiException>().firstOrNull()?.let { throw it }
        throw ApiException(503, "STORE_UNAVAILABLE", "Storage unavailable. Retry with the same request ID.")
    } catch (error: TimeoutException) {
        // A timed-out write may still commit. Its receipt makes a retry safe.
        throw ApiException(503, "STORE_TIMEOUT", "Storage timed out. Retry with the same request ID.")
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw ApiException(503, "STORE_INTERRUPTED", "Request interrupted. Retry with the same request ID.")
    }

    companion object {
        const val ROOMS = "authoritativeRooms"
        const val REQUESTS = "authoritativeRequests"
    }
}
