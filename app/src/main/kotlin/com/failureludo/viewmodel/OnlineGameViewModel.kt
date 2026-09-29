package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.FeedbackSettings
import com.failureludo.data.GameSessionStore
import kotlinx.coroutines.launch
import com.failureludo.data.GamePreferencesStore
import com.failureludo.data.online.OnlineGameRepository
import com.failureludo.engine.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject

/** Online state is owned by the server. This ViewModel only submits intentions. */
class OnlineGameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OnlineGameRepository.get(application)
    val state = repository.state
    val feedbackSettings = GamePreferencesStore(application).feedbackSettings.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), FeedbackSettings())
    private val choice = MutableStateFlow<Pair<Long, Piece>?>(null)
    val homeEntryChoice = choice.asStateFlow()
    private val colors = MutableStateFlow(defaultPlayerColors())
    val palette = colors.asStateFlow()
    init {
        repository.attach()
        viewModelScope.launch {
            runCatching { GameSessionStore(application).loadSetupState() }.getOrNull()?.let { colors.value = it.playerColors }
        }
    }
    fun retry() = repository.retry()
    fun returnToGame() = repository.command("RETURN")
    fun resign(confirmedRevision: Long) {
        val session = state.value
        val room = session.room ?: return
        if (session.canAct && room.canResign(session.uid) && room.revision == confirmedRevision) {
            choice.value = null
            repository.command("RESIGN")
        }
    }
    fun rollDice() {
        val session = state.value
        val game = session.room?.game ?: return
        if (canAct() && game.turnPhase == TurnPhase.WAITING_FOR_ROLL) repository.command("ROLL")
    }
    fun selectPiece(piece: Piece) {
        val room = state.value.room ?: return
        val game = room.game ?: return
        if (!canAct() || game.turnPhase != TurnPhase.WAITING_FOR_PIECE_SELECTION || piece !in game.movablePieces) return
        if (GameRules.wouldEnterHomePath(piece, game.lastDice!!.value, piece.color, game.players, game.mode)) {
            choice.value = room.revision to piece
        } else submit(piece, false)
    }
    fun dismissHomeEntryChoice() { choice.value = null }
    fun resolveHomeEntryChoice(enter: Boolean) {
        val pending = choice.value ?: return
        choice.value = null
        if (state.value.room?.revision == pending.first) submit(pending.second, !enter)
    }
    private fun submit(piece: Piece, defer: Boolean) {
        val game = state.value.room?.game ?: return
        if (!canAct() || piece !in game.movablePieces) return
        val owner = game.players.single { it.color == piece.color }
        repository.command("MOVE", JSONObject().put("playerId", owner.id.value).put("pieceId", piece.id)
            .put("deferHomeEntry", defer))
    }
    private fun canAct(): Boolean {
        val session = state.value
        val room = session.room ?: return false
        return session.canPlay && room.game?.let { room.controllerUid(it.currentPlayer.color) == session.uid } == true &&
            room.game?.isGameOver == false
    }
    override fun onCleared() { repository.detach() }
}
