package com.failureludo.server

import com.failureludo.online.*

import com.failureludo.engine.GameMode
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.logging.Logger

fun interface TokenVerifier { fun verify(token: String): String }

class ApiHandler(private val controller: GameController, private val tokens: TokenVerifier,
                 private val nowMillis: () -> Long = System::currentTimeMillis) : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            if (path == "/healthz" && exchange.requestMethod == "GET") {
                respond(exchange, 200, JSONObject().put("status", "ok"))
                return
            }
            val authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty()
            if (!authorization.startsWith("Bearer ") || authorization.length > 8192) {
                reject(401, "UNAUTHENTICATED", "A Firebase ID token is required.")
            }
            val token = authorization.removePrefix("Bearer ").trim()
            if (token.isEmpty()) reject(401, "UNAUTHENTICATED", "A Firebase ID token is required.")
            val uid = tokens.verify(token)
            val segments = path.trim('/').split('/')
            val response = when {
                path == "/v1/rooms" && exchange.requestMethod == "POST" -> {
                    val body = body(exchange)
                    body.only("requestId", "name", "maxPlayers", "mode")
                    val mode = when (body.getString("mode")) {
                        "FREE_FOR_ALL" -> GameMode.FREE_FOR_ALL
                        "TEAM" -> GameMode.TEAM
                        else -> reject(400, "INVALID_MODE", "Unknown game mode.")
                    }
                    result(controller.create(uid, body.getString("requestId"), body.getString("name"),
                        body.strictInt("maxPlayers"), mode))
                }
                segments.size == 3 && segments.take(2) == listOf("v1", "rooms") && exchange.requestMethod == "GET" ->
                    JSONObject().put("room", RoomCodec.encode(controller.get(uid, segments[2])))
                segments.size == 4 && segments.take(2) == listOf("v1", "rooms") && segments[3] == "commands" &&
                    exchange.requestMethod == "POST" -> {
                    val body = body(exchange)
                    val type = body.getString("type")
                    val common = arrayOf("requestId", "type", "expectedRevision")
                    val command = when (type) {
                        "JOIN" -> {
                            body.only("requestId", "type", "name")
                            Command.Join(body.getString("name"))
                        }
                        "START" -> { body.only(*common); Command.Start }
                        "LEAVE" -> { body.only(*common); Command.Leave }
                        "RESIGN" -> { body.only(*common); Command.Resign }
                        "CHECK_TIMEOUT" -> { body.only(*common); Command.CheckTimeout }
                        "RETURN" -> { body.only(*common); Command.Return }
                        "ROLL" -> { body.only(*common); Command.Roll }
                        "MOVE" -> {
                            body.only(*common, "playerId", "pieceId", "deferHomeEntry")
                            val defer = body.get("deferHomeEntry")
                            if (defer !is Boolean) reject(400, "INVALID_BODY", "deferHomeEntry must be boolean.")
                            Command.Move(body.strictInt("playerId"), body.strictInt("pieceId"), defer)
                        }
                        else -> reject(400, "INVALID_COMMAND", "Unknown command.")
                    }
                    result(controller.execute(uid, segments[2], body.getString("requestId"),
                        if (command is Command.Join) null else body.strictLong("expectedRevision"), command))
                }
                else -> reject(404, "NOT_FOUND", "Endpoint not found.")
            }
            respond(exchange, 200, response)
        } catch (error: ApiException) {
            respond(exchange, error.status, JSONObject().put("error", error.code).put("message", error.message))
        } catch (error: UnsupportedRoomVersion) {
            respond(exchange, 409, JSONObject().put("error", "UNSUPPORTED_VERSION").put("message", error.message))
        } catch (error: JSONException) {
            respond(exchange, 400, JSONObject().put("error", "INVALID_BODY").put("message", "Invalid JSON request."))
        } catch (error: Exception) {
            // Never log bearer tokens, request bodies, or backend exception messages.
            logger.warning("Request failed: ${error.javaClass.simpleName}")
            respond(exchange, 500, JSONObject().put("error", "INTERNAL_ERROR")
                .put("message", "Request failed. Retry with the same request ID."))
        } finally {
            exchange.close()
        }
    }

    private fun body(exchange: HttpExchange): JSONObject {
        if (!exchange.requestHeaders.getFirst("Content-Type").orEmpty().substringBefore(';')
                .trim().equals("application/json", ignoreCase = true)) {
            reject(415, "CONTENT_TYPE", "Use application/json.")
        }
        val bytes = exchange.requestBody.readNBytes(4097)
        if (bytes.size > 4096) reject(413, "BODY_TOO_LARGE", "Request exceeds 4 KiB.")
        val parser = JSONTokener(bytes.toString(Charsets.UTF_8))
        val value = parser.nextValue()
        if (value !is JSONObject || parser.nextClean() != '\u0000') reject(400, "INVALID_BODY", "Expected one JSON object.")
        return value
    }

    private fun JSONObject.only(vararg fields: String) {
        if (keySet().any { it !in fields }) reject(400, "UNKNOWN_FIELD", "Unexpected request field.")
    }

    private fun JSONObject.strictLong(key: String): Long {
        val value = get(key)
        if (value !is Int && value !is Long) reject(400, "INVALID_BODY", "$key must be an integer.")
        return (value as Number).toLong()
    }

    private fun JSONObject.strictInt(key: String): Int {
        val value = strictLong(key)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) reject(400, "INVALID_BODY", "$key is out of range.")
        return value.toInt()
    }

    private fun result(result: CommandResult): JSONObject = JSONObject()
        .put("room", RoomCodec.encode(result.room)).put("acceptedRevision", result.acceptedRevision)
        .put("duplicate", result.duplicate)

    private fun respond(exchange: HttpExchange, status: Int, json: JSONObject) {
        val bytes = json.put("serverTimeMillis", nowMillis()).toString().toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.set("Cache-Control", "no-store")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    companion object { private val logger = Logger.getLogger(ApiHandler::class.java.name) }
}
