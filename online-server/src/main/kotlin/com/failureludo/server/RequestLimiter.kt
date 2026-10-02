package com.failureludo.server

import com.google.cloud.firestore.DocumentSnapshot
import com.google.cloud.firestore.Firestore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

fun interface RequestLimiter { fun check(uid: String) }

/** Counts all authenticated HTTP requests, including reads and invalid commands. */
class FirestoreRequestLimiter(
    private val db: Firestore, private val nowMillis: () -> Long = System::currentTimeMillis
) : RequestLimiter {
    override fun check(uid: String): Unit = firestoreCall {
        db.runTransaction { tx ->
            val ref = quotaRef(db, "http", uid)
            val next = RateWindow.consume(tx.get(ref).get(), nowMillis(), 120, 60_000)
            tx.set(ref, next)
            Unit
        }.get(15, TimeUnit.SECONDS)
    }
}

internal fun quotaRef(db: Firestore, scope: String, uid: String) = db.collection("authoritativeLimits")
    .document(scope + "-" + MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) })

internal object RateWindow {
    fun consume(doc: DocumentSnapshot, now: Long, limit: Long, duration: Long): Map<String, Long> {
        val until = doc.getLong("untilMillis") ?: now
        val count = if (now >= until) 0L else requireNotNull(doc.getLong("count"))
        if (count >= limit) throw ApiException(429, "RATE_LIMITED",
            "Too many requests. Wait ${((until - now + 999) / 1000).coerceAtLeast(1)} seconds and retry with the same request ID.",
            retryAfterSeconds = ((until - now + 999) / 1000).coerceAtLeast(1))
        return mapOf("untilMillis" to if (now >= until) now + duration else until, "count" to count + 1)
    }
}
