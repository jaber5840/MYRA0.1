package com.myra.assistant.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.myra.assistant.ai.DeviceCommandExecutor
import com.myra.assistant.model.AppCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val executor = DeviceCommandExecutor(application)

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult

    fun executeCommand(command: AppCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = executor.executeCommand(command)
            if (!result.isNullOrEmpty()) {
                _commandResult.postValue(result)
            }
        }
    }

    fun acceptCall() {
        executor.acceptCall()?.let { _commandResult.postValue(it) }
    }

    fun rejectCall() {
        executor.rejectCall()?.let { _commandResult.postValue(it) }
    }
}
