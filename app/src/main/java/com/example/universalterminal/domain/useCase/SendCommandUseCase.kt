package com.example.universalterminal.domain.useCase

import android.util.Log
import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class SendCommandUseCase @Inject constructor(private val repository: BleRepository) {
    suspend fun invoke(command: String) : ByteArray {
        Log.i("SendCommandUseCase", "Command: $command")
       return repository.sendCommand(command)
    }
}
