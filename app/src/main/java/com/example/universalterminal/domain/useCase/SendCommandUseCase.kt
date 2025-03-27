package com.example.universalterminal.domain.useCase

import android.util.Log
import com.example.universalterminal.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SendCommandUseCase @Inject constructor(private val repository: BleRepository) {
    suspend fun invoke(command: String) : Flow<ByteArray> {
        Log.i("SendCommandUseCase", "Command: $command")
       return repository.sendCommand(command)
    }
}