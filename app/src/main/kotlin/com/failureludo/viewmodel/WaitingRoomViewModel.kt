package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.failureludo.data.auth.AuthRepository
import com.failureludo.data.online.GameRoom
import com.failureludo.data.online.OnlineGameRepository
import com.failureludo.data.online.RoomStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed class WaitingRoomState {
    object Loading : WaitingRoomState()
    data class Waiting(val room: GameRoom, val currentUid: String) : WaitingRoomState()
    data class GameStarting(val roomId: String) : WaitingRoomState()
    object Disbanded : WaitingRoomState()
}

class WaitingRoomViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepo = AuthRepository(application)
    private val onlineRepo = OnlineGameRepository()

    private val _state = MutableStateFlow<WaitingRoomState>(WaitingRoomState.Loading)
    val state: StateFlow<WaitingRoomState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var currentRoomId: String? = null
    private var roomListener: Job? = null
    private var isLeaving = false

    fun init(roomId: String) {
        if (currentRoomId == roomId) return
        currentRoomId = roomId
        val uid = authRepo.currentProfile?.uid ?: return

        roomListener = viewModelScope.launch {
            onlineRepo.listenToRoom(roomId).collect { room ->
                when {
                    room == null -> _state.value = WaitingRoomState.Disbanded
                    room.status == RoomStatus.IN_PROGRESS -> _state.value = WaitingRoomState.GameStarting(roomId)
                    else -> _state.value = WaitingRoomState.Waiting(room, uid)
                }
            }
        }
    }

    fun startGame() {
        val roomId = currentRoomId ?: return
        val uid = authRepo.currentProfile?.uid ?: return
        viewModelScope.launch {
            onlineRepo.startGame(roomId, uid).onFailure {
                _errors.emit("Could not start game. Are you the host?")
            }
        }
    }

    fun leaveRoom(onLeft: () -> Unit) {
        if (isLeaving) return
        val roomId = currentRoomId
        val uid = authRepo.currentProfile?.uid
        if (roomId == null || uid == null) {
            onLeft()
            return
        }
        isLeaving = true
        // Stop listener-driven navigation before deleting our membership. Keep
        // this destination alive until the best-effort leave has completed.
        roomListener?.cancel()
        _state.value = WaitingRoomState.Loading
        viewModelScope.launch {
            withTimeoutOrNull(5_000L) { onlineRepo.leaveRoom(roomId, uid) }
            onLeft()
        }
    }
}
