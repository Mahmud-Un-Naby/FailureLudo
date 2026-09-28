package com.failureludo.server

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.cloud.FirestoreClient
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun main() {
    val project = requireNotNull(System.getenv("GOOGLE_CLOUD_PROJECT")) { "GOOGLE_CLOUD_PROJECT is required" }
    val port = (System.getenv("PORT") ?: "8080").toInt().also { require(it in 1..65535) }
    // Never accept emulator-issued identities in a deployed Cloud Run service.
    if (System.getenv("K_SERVICE") != null) {
        check(System.getenv("FIREBASE_AUTH_EMULATOR_HOST") == null && System.getenv("FIRESTORE_EMULATOR_HOST") == null)
    }
    val app = FirebaseApp.initializeApp(FirebaseOptions.builder()
        .setProjectId(project).setCredentials(GoogleCredentials.getApplicationDefault()).build())
    val auth = FirebaseAuth.getInstance(app)
    val db = FirestoreClient.getFirestore(app)
    val verifier = TokenVerifier { token ->
        try {
            auth.verifyIdToken(token, true).uid
        } catch (error: FirebaseAuthException) {
            reject(401, "UNAUTHENTICATED", "The session is invalid or expired. Refresh it and retry.")
        }
    }
    val executor = ThreadPoolExecutor(16, 16, 0, TimeUnit.SECONDS, ArrayBlockingQueue(64),
        ThreadPoolExecutor.AbortPolicy())
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 64)
    server.executor = executor
    server.createContext("/", ApiHandler(GameController(FirestoreRoomStore(db)), verifier))
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop(5)
        executor.shutdown()
        db.close()
        app.delete()
    })
    server.start()
    println("Failure Ludo online API listening on port $port")
}
