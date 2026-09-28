package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.failureludo.data.online.OnlineGameRepository
import com.failureludo.engine.GameMode

class OnlineLobbyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OnlineGameRepository.get(application)
    val state = repository.state
    init { repository.attach() }
    fun createRoom(players: Int, mode: GameMode) = repository.create(players, mode)
    fun joinRoom(code: String) = repository.join(code)
    fun retry() = repository.retry()
    fun forgetFinished() = repository.forgetFinished()
    override fun onCleared() { repository.detach() }
}
