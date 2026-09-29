package com.failureludo.server

import com.failureludo.online.*

import com.failureludo.engine.*
import org.junit.Assert.*
import org.junit.Test

class RoomCodecTest {
    @Test fun `snapshot preserves pair identity team flags positions and events`() {
        val initial = GameEngine.newGame(PlayerColor.entries, mode = GameMode.TEAM)
        val pieces = listOf(
            Piece(0, PlayerColor.RED, PiecePosition.HomeBase),
            Piece(1, PlayerColor.RED, PiecePosition.MainTrack(50), 27, "pair-1"),
            Piece(2, PlayerColor.RED, PiecePosition.HomeColumn(5), 28),
            Piece(3, PlayerColor.RED, PiecePosition.Finished, 29)
        )
        val id = PlayerId(1)
        val state = initial.copy(players = initial.players.map { if (it.id == id) it.copy(pieces = pieces) else it },
            moveCounter = 30, lastDice = DiceResult(6, 2), movablePieces = listOf(pieces[1]),
            turnPhase = TurnPhase.WAITING_FOR_PIECE_SELECTION,
            hasEnteredBoardAtLeastOnce = initial.hasEnteredBoardAtLeastOnce + (id to true),
            sharedTeamDiceEnabled = setOf(0), winners = listOf(id),
            eventLog = listOf(GameEvent.PieceMoved(id, PlayerColor.RED, 1),
                GameEvent.PieceEnteredBoard(id, PlayerColor.RED, 1),
                GameEvent.PieceCaptured(PlayerId(2), PlayerColor.BLUE, id, PlayerColor.RED),
                GameEvent.PieceFinished(id, PlayerColor.RED, 3), GameEvent.PlayerWon(listOf(id)),
                GameEvent.ExtraRollGranted(id, PlayerColor.RED, "Capture"),
                GameEvent.TurnSkipped(id, PlayerColor.RED), GameEvent.ConsecutiveSixesForfeit(id, PlayerColor.RED)))
        val room = OnlineRoom("ABCDEFGH", "host", 4, GameMode.TEAM,
            listOf(Member("host", "Host", PlayerColor.RED)), 42, RoomStatus.PLAYING, state, LastAction("ROLL", "host", 6, 2))
        assertEquals(room, RoomCodec.decode(RoomCodec.encode(room)))
    }
    @Test fun `resignation and team controller ownership survive snapshot round trips`() {
        val room = OnlineRoom("ABCDEFGH", "host", 4, GameMode.TEAM, listOf(
            Member("host", "Host", PlayerColor.RED, resigned = true),
            Member("blue", "Blue", PlayerColor.BLUE), Member("yellow", "Yellow", PlayerColor.YELLOW),
            Member("green", "Green", PlayerColor.GREEN)), status = RoomStatus.PLAYING,
            game = GameEngine.newGame(PlayerColor.entries, mode = GameMode.TEAM))
        val decoded = RoomCodec.decode(RoomCodec.encode(room))
        assertEquals(room, decoded)
        assertEquals("yellow", decoded.controllerUid(PlayerColor.RED))
        assertTrue(decoded.hasResigned("host"))
        assertTrue(decoded.canForget("host"))
        assertFalse(decoded.canForget("yellow"))
        assertFalse(decoded.canResign("host"))
        assertTrue(decoded.canResign("yellow"))
    }

    @Test fun `legacy snapshots migrate without inventing resignations or discarding game state`() {
        val room = OnlineRoom("ABCDEFGH", "host", 2, GameMode.FREE_FOR_ALL,
            listOf(Member("host", "Host", PlayerColor.RED)), game = GameEngine.newGame(PlayerColor.entries.take(2)))
        val json = RoomCodec.encode(room).put("protocolVersion", 1).put("rulesVersion", "2026-09-23")
        json.getJSONArray("members").getJSONObject(0).remove("resigned")
        val decoded = RoomCodec.decode(json)
        assertEquals(room, decoded)
        val upgraded = RoomCodec.encode(decoded)
        assertEquals(PROTOCOL_VERSION, upgraded.getInt("protocolVersion"))
        assertEquals(RULES_VERSION, upgraded.getString("rulesVersion"))
        assertFalse(upgraded.getJSONArray("members").getJSONObject(0).getBoolean("resigned"))
        assertThrows(UnsupportedRoomVersion::class.java) { RoomCodec.decode(json.put("rulesVersion", RULES_VERSION)) }
    }

    @Test fun `AFK state and deadlines survive codec while v2 rooms remain untimed`() {
        val room = OnlineRoom("ABCDEFGH", "host", 2, GameMode.FREE_FOR_ALL,
            listOf(Member("host", "Host", PlayerColor.RED, afkSinceMillis = 100_000),
                Member("guest", "Guest", PlayerColor.BLUE)), status = RoomStatus.PLAYING,
            actionTimeoutMillis = 10_000, actionDeadlineAtMillis = 230_000, afkTimeoutMillis = 120_000)
        val decoded = RoomCodec.decode(RoomCodec.encode(room))
        assertEquals(room, decoded)
        assertEquals(220_000L, decoded.nextDeadlineAtMillis())
        assertNull(decoded.copy(status = RoomStatus.FINISHED).nextDeadlineAtMillis())
        val old = RoomCodec.encode(room.copy(members = room.members.map { it.copy(resigned = true) }))
            .put("protocolVersion", 2).put("rulesVersion", "2026-09-29")
        old.remove("actionTimeoutMillis"); old.remove("actionDeadlineAtMillis"); old.remove("afkTimeoutMillis")
        for (i in 0 until old.getJSONArray("members").length()) old.getJSONArray("members").getJSONObject(i).remove("afkSinceMillis")
        val legacy = RoomCodec.decode(old)
        assertTrue(legacy.members.all { it.resigned })
        assertTrue(legacy.members.all { it.afkSinceMillis == null })
        assertNull(legacy.actionTimeoutMillis)
        assertNull(legacy.nextDeadlineAtMillis())
    }

    @Test fun `unsupported protocol and rules fail closed`() {
        val room = OnlineRoom("ABCDEFGH", "host", 2, GameMode.FREE_FOR_ALL, listOf(Member("host", "Host", PlayerColor.RED)))
        for ((key, value) in listOf("protocolVersion" to 999, "rulesVersion" to "unknown")) {
            val error = assertThrows(UnsupportedRoomVersion::class.java) { RoomCodec.decode(RoomCodec.encode(room).put(key, value)) }
            assertTrue(error.message!!.contains("compatible"))
        }
    }
}
