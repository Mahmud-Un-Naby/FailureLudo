package com.failureludo.engine

/**
 * The central coordinator that drives game flow.
 *
 * All public functions are pure (return a new [GameState]) so they can be called
 * from any thread, stored, and later replayed or transmitted over a network.
 *
 * Typical call sequence:
 *   1. [rollDice]  — on each turn, generates a dice value and evaluates movable pieces
 *   2. [selectPiece] — player chooses which piece to move
 *   3. Repeat until [GameState.isGameOver]
 */
object GameEngine {

    private val dice = DiceRoller()

    // ── Factory ──────────────────────────────────────────────────────────────

    /**
     * Creates a fresh [GameState] for a new game.
     *
     * @param activeColors   Which of the 4 colours are active (2-4 items).
     *                       Order determines turn order.
     * @param playerTypes    Map of colour → human or bot.
     * @param playerNames    Custom display names (optional).
     * @param mode           FFA or Team.
     */
    fun newGame(
        activeColors: List<PlayerColor>,
        playerTypes: Map<PlayerColor, PlayerType> = emptyMap(),
        playerNames: Map<PlayerColor, String> = emptyMap(),
        mode: GameMode = GameMode.FREE_FOR_ALL
    ): GameState {
        require(activeColors.size in 2..4) { "Need between 2 and 4 active colours." }
        if (mode == GameMode.TEAM) {
            require(activeColors.size == 4) { "Team mode requires exactly 4 players." }
        }

        // All 4 colours are always created; inactive ones are just marked inactive
        val players = PlayerColor.entries.map { color ->
            Player(
                id = PlayerId(color.ordinal + 1),
                color = color,
                name = playerNames[color] ?: "Player-${color.ordinal + 1}",
                type = playerTypes[color] ?: PlayerType.HUMAN,
                isActive = color in activeColors
            )
        }

        // First turn: first ACTIVE player in the activeColors list
        val startIndex = players.indexOfFirst { it.color == activeColors.first() }

        return GameState(
            players = players,
            mode = mode,
            moveCounter = 0L,
            currentPlayerIndex = startIndex,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            diceByPlayer = PlayerColor.entries.associate { PlayerId(it.ordinal + 1) to null }
        )
    }

    // ── Turn Actions ─────────────────────────────────────────────────────────

    /**
     * Applies a full deterministic turn for replay/import use-cases.
     *
     * This helper enforces actor ownership, injects a known dice value, and then
     * applies the chosen piece movement in one operation.
     */
    fun applyDeterministicTurn(state: GameState, input: DeterministicTurnInput): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
            "applyDeterministicTurn called in wrong phase: ${state.turnPhase}"
        }
        require(state.currentPlayer.id == input.actorId) {
            "Actor ${input.actorId.value} is not the current player ${state.currentPlayer.id.value}."
        }

        val rolled = rollDice(state, input.diceValue)
        require(rolled.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION) {
            "Deterministic move requires a selectable piece, but phase is ${rolled.turnPhase}."
        }

        val movingPlayer = requireNotNull(rolled.players.firstOrNull { it.id == input.movingPlayerId }) {
            "Moving player ${input.movingPlayerId.value} not found."
        }
        val selectedPiece = requireNotNull(
            rolled.movablePieces.firstOrNull { piece ->
                piece.color == movingPlayer.color && piece.id == input.pieceId
            }
        ) {
            "Piece ${input.pieceId} for player ${input.movingPlayerId.value} is not movable."
        }

        return selectPiece(rolled, selectedPiece, input.deferHomeEntry)
    }

    /**
     * Applies a deterministic roll that should not produce a piece selection.
     *
     * Used for replay entries where a player rolled but had no legal move, or
     * forfeited via three consecutive sixes.
     */
    fun applyDeterministicRollOnly(state: GameState, actorId: PlayerId, diceValue: Int): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
            "applyDeterministicRollOnly called in wrong phase: ${state.turnPhase}"
        }
        require(state.currentPlayer.id == actorId) {
            "Actor ${actorId.value} is not the current player ${state.currentPlayer.id.value}."
        }

        val rolled = rollDice(state, diceValue)
        return when (rolled.turnPhase) {
            TurnPhase.NO_MOVES_AVAILABLE -> advanceNoMoves(rolled)
            TurnPhase.WAITING_FOR_ROLL -> rolled // consecutive-sixes forfeit already advanced turn
            TurnPhase.WAITING_FOR_PIECE_SELECTION -> {
                throw IllegalArgumentException(
                    "Deterministic roll-only is invalid because movable pieces exist."
                )
            }
            TurnPhase.GAME_OVER -> rolled
        }
    }

    /**
     * Rolls the dice for the current player.
     * Updates [TurnPhase] to WAITING_FOR_PIECE_SELECTION or NO_MOVES_AVAILABLE.
     */
    fun rollDice(state: GameState): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
            "rollDice called in wrong phase: ${state.turnPhase}"
        }

        return rollDiceWithValue(state, dice.roll())
    }

    /**
     * Rolls with a forced dice value for deterministic replay/import paths.
     */
    fun rollDice(state: GameState, forcedDiceValue: Int): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_ROLL) {
            "rollDice called in wrong phase: ${state.turnPhase}"
        }
        require(forcedDiceValue in 1..6) {
            "Forced dice value must be 1..6, got $forcedDiceValue."
        }

        return rollDiceWithValue(state, forcedDiceValue)
    }

    private fun rollDiceWithValue(state: GameState, diceValue: Int): GameState {
        val rollCount = if (diceValue == 6) {
            (state.lastDice?.takeIf { it.value == 6 }?.rollCount ?: 0) + 1
        } else 0

        // Three consecutive 6s → forfeit turn
        if (diceValue == 6 && rollCount == 3) {
            val event = GameEvent.ConsecutiveSixesForfeit(
                playerId = state.currentPlayer.id,
                color = state.currentPlayer.color
            )
            val updatedDiceByPlayer = state.diceByPlayer.toMutableMap().apply {
                this[state.currentPlayer.id] = diceValue
            }
            return advanceToNextTurn(
                state.copy(
                    lastDice = DiceResult(diceValue, rollCount),
                    diceByPlayer = updatedDiceByPlayer,
                    eventLog = state.eventLog + event
                )
            )
        }

        val diceResult = DiceResult(diceValue, rollCount)
        val movable = GameRules.movablePiecesForTurn(
            currentPlayer = state.currentPlayer,
            diceValue = diceValue,
            allPlayers = state.players,
            mode = state.mode,
            sharedTeamDiceEnabled = state.sharedTeamDiceEnabled
        )

        val newPhase = if (movable.isEmpty()) TurnPhase.NO_MOVES_AVAILABLE
                       else TurnPhase.WAITING_FOR_PIECE_SELECTION

        val updatedDiceByPlayer = state.diceByPlayer.toMutableMap().apply {
            this[state.currentPlayer.id] = diceValue
        }

        return state.copy(
            lastDice = diceResult,
            diceByPlayer = updatedDiceByPlayer,
            movablePieces = movable,
            turnPhase = newPhase
        )
    }

    /**
     * Auto-advances when there are no moves (caller should invoke after a short UI delay).
     */
    fun advanceNoMoves(state: GameState): GameState {
        require(state.turnPhase == TurnPhase.NO_MOVES_AVAILABLE)
        val event = GameEvent.TurnSkipped(
            playerId = state.currentPlayer.id,
            color = state.currentPlayer.color
        )
        return advanceToNextTurn(state.copy(eventLog = state.eventLog + event))
    }

    /**
     * Moves the selected [piece] using the current dice value.
     *
     * Returns the updated [GameState], which may include:
     * - A capture event
     * - An extra-roll turn (dice == 6 or capture occurred)
     * - A win condition
     * - Normal turn advancement
     */
    fun selectPiece(state: GameState, piece: Piece, deferHomeEntry: Boolean = false): GameState {
        require(state.turnPhase == TurnPhase.WAITING_FOR_PIECE_SELECTION) {
            "selectPiece called in wrong phase: ${state.turnPhase}"
        }
        require(piece in state.movablePieces) { "Piece ${piece.id} is not movable." }

        val diceValue = state.lastDice!!.value
        val movingPlayer = state.players.firstOrNull { it.color == piece.color }
            ?: error("Moving player not found for color ${piece.color}")
        require(GameRules.canMove(piece, diceValue, movingPlayer, state.players, state.mode, deferHomeEntry)) {
            "Selected move route is not legal."
        }
        val movingPlayerId = movingPlayer.id
        val movingColor = piece.color
        val actingPlayerId = state.currentPlayer.id
        val actingColor = state.currentPlayer.color

        // Compute destination
        val destination = GameRules.computeDestination(
            piece,
            diceValue,
            movingColor,
            state.players,
            state.mode,
            deferHomeEntry
        )!!

        // Count captures BEFORE applying move
        val captureTargets = GameRules.captureTargets(
            piece,
            diceValue,
            movingColor,
            state.players,
            state.mode,
            deferHomeEntry
        )
        val captureCount = captureTargets.size

        // Apply the move
        val newMoveCounter = state.moveCounter + 1
        val newPlayers = GameRules.applyMove(
            piece = piece,
            diceValue = diceValue,
            color = movingColor,
            players = state.players,
            mode = state.mode,
            deferHomeEntry = deferHomeEntry,
            movedAt = newMoveCounter
        )

        // Build event list
        val events = mutableListOf<GameEvent>()
        val isEntering = piece.position is PiecePosition.HomeBase
        if (isEntering) {
            events += GameEvent.PieceEnteredBoard(
                playerId = movingPlayerId,
                color = movingColor,
                pieceId = piece.id
            )
        } else {
            events += GameEvent.PieceMoved(playerId = movingPlayerId, color = movingColor, pieceId = piece.id)
        }
        if (destination is PiecePosition.Finished) {
            events += GameEvent.PieceFinished(
                playerId = movingPlayerId,
                color = movingColor,
                pieceId = piece.id
            )
        }

        // Capture events
        captureTargets.forEach { target ->
            val capturedPlayerId = state.players.firstOrNull { it.color == target.color }?.id
                ?: error("Captured player not found for color ${target.color}")
            events += GameEvent.PieceCaptured(
                capturedPlayerId = capturedPlayerId,
                capturedColor = target.color,
                byPlayerId = actingPlayerId,
                byColor = movingColor
            )
        }

        val updatedEnteredBoardFlags = state.hasEnteredBoardAtLeastOnce.toMutableMap().apply {
            if (isEntering) {
                this[movingPlayerId] = true
            }
        }

        val newState = state.copy(
            players = newPlayers,
            moveCounter = newMoveCounter,
            eventLog = state.eventLog + events,
            hasEnteredBoardAtLeastOnce = updatedEnteredBoardFlags,
            sharedTeamDiceEnabled = computeSharedTeamDiceEnabled(
                state.copy(hasEnteredBoardAtLeastOnce = updatedEnteredBoardFlags)
            ),
            movablePieces = emptyList()
        )

        // Check winner
        val winners = GameRules.checkWinner(newPlayers, state.mode)
        if (winners != null) {
            return newState.copy(
                winners = winners,
                turnPhase = TurnPhase.GAME_OVER,
                eventLog = newState.eventLog + GameEvent.PlayerWon(winners)
            )
        }

        // Extra roll?
        val captured = captureCount > 0
        val extraRoll = GameRules.grantsExtraRoll(diceValue, piece, newPlayers[state.currentPlayerIndex].pieces, captured)
        return if (extraRoll) {
            val reason = if (diceValue == 6) "rolled a 6" else "captured a piece"
            newState.copy(
                turnPhase = TurnPhase.WAITING_FOR_ROLL,
                lastDice = DiceResult(diceValue, state.lastDice.rollCount),
                eventLog = newState.eventLog + GameEvent.ExtraRollGranted(
                    playerId = actingPlayerId,
                    color = actingColor,
                    reason = reason
                )
            )
        } else {
            advanceToNextTurn(newState)
        }
    }

    /**
     * Forfeit one FFA player or an entire team. Online controller handoff is handled
     * outside the engine; call this for TEAM only once both teammates have resigned.
     * Pawns belonging to forfeited seats leave play; other players keep their roll.
     */
    fun forfeit(state: GameState, playerId: PlayerId): GameState {
        require(!state.isGameOver) { "The game is already over." }
        val player = requireNotNull(state.players.find { it.id == playerId && it.isActive }) {
            "Only an active player can forfeit."
        }
        val removed = state.players.filter {
            it.isActive && (it.id == playerId ||
                (state.mode == GameMode.TEAM && it.color.teamIndex == player.color.teamIndex))
        }.map { it.id }.toSet()
        val players = GameRules.normalizePairs(state.players.map {
            if (it.id in removed) it.copy(isActive = false) else it
        }, state.mode)
        val remaining = players.filter { it.isActive }
        require(remaining.isNotEmpty()) { "A game must retain a winning side." }
        val next = state.copy(players = players,
            diceByPlayer = state.diceByPlayer.mapValues { (id, value) -> if (id in removed) null else value })
        if (state.mode == GameMode.TEAM || remaining.size == 1) {
            val winners = remaining.map { it.id }
            return next.copy(turnPhase = TurnPhase.GAME_OVER, winners = winners,
                lastDice = null, movablePieces = emptyList(),
                eventLog = next.eventLog + GameEvent.PlayerWon(winners))
        }
        if (state.currentPlayer.id in removed) {
            return advanceToNextTurn(next.copy(movablePieces = emptyList()))
        }
        if (state.turnPhase in listOf(TurnPhase.WAITING_FOR_PIECE_SELECTION, TurnPhase.NO_MOVES_AVAILABLE)) {
            // Removing an opponent's barrier may make previously blocked moves legal.
            val movable = GameRules.movablePiecesForTurn(next.currentPlayer, requireNotNull(next.lastDice).value,
                next.players, next.mode, next.sharedTeamDiceEnabled)
            val refreshed = next.copy(movablePieces = movable,
                turnPhase = if (movable.isEmpty()) TurnPhase.NO_MOVES_AVAILABLE else TurnPhase.WAITING_FOR_PIECE_SELECTION)
            return if (movable.isEmpty()) advanceNoMoves(refreshed) else refreshed
        }
        return next
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun advanceToNextTurn(state: GameState): GameState {
        val nextIdx = GameRules.nextPlayerIndex(state.currentPlayerIndex, state.players)
        val sharedTeamDiceEnabled = computeSharedTeamDiceEnabled(state)
        return state.copy(
            currentPlayerIndex = nextIdx,
            turnPhase = TurnPhase.WAITING_FOR_ROLL,
            lastDice = null,
            sharedTeamDiceEnabled = sharedTeamDiceEnabled
        )
    }

    private fun computeSharedTeamDiceEnabled(state: GameState): Set<Int> {
        if (state.mode != GameMode.TEAM) return emptySet()

        val unlocked = state.sharedTeamDiceEnabled.toMutableSet()
        val activeByTeam = state.players
            .filter { it.isActive }
            .groupBy { it.color.teamIndex }

        activeByTeam.forEach { (teamIndex, teamPlayers) ->
            if (teamPlayers.size < 2) return@forEach
            val bothHaveEntered = teamPlayers.all { teammate ->
                state.hasEnteredBoardAtLeastOnce[teammate.id] == true
            }
            if (bothHaveEntered) {
                unlocked += teamIndex
            }
        }

        return unlocked
    }

}
