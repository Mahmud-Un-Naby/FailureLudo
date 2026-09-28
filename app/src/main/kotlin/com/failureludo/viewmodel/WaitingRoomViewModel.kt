package com.failureludo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.failureludo.data.online.OnlineGameRepository

class WaitingRoomViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = OnlineGameRepository.get(application)
    val state = repository.state
    init { repository.attach() }
    fun startGame() = repository.command("START")
    fun leaveRoom() = repository.command("LEAVE")
    fun retry() = repository.retry()
    override fun onCleared() { repository.detach() }
}
