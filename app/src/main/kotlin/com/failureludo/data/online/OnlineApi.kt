package com.failureludo.data.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Never follows redirects with a bearer token. Only the build-configured HTTPS origin is used. */
internal class OnlineApi(private val origin: String, private val onServerTime: (Long) -> Unit = {}) {
    suspend fun request(path: String, token: String, body: String? = null): JSONObject = withContext(Dispatchers.IO) {
        require(origin.startsWith("https://") && path.startsWith("/v1/rooms"))
        val connection = URL(origin + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 40_000
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (output.length + count > 262144) throw IOException("Online response is too large.")
                    output.append(buffer, 0, count)
                }
                output.toString()
            }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrNull()
            json?.optLong("serverTimeMillis", 0)?.takeIf { it > 0 }?.let(onServerTime)
            if (status !in 200..299) throw OnlineApiException(status,
                json?.optString("error") ?: "HTTP_ERROR",
                json?.optString("message")?.takeIf { it.isNotBlank() }
                    ?: "Online service unavailable. Please retry.")
            json ?: throw IOException("Invalid online response. Retry to recover your action.")
        } finally { connection.disconnect() }
    }
}
