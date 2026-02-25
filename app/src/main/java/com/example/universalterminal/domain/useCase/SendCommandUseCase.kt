package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class SendCommandUseCase @Inject constructor(private val repository: BleRepository) {
    suspend fun invoke(command: String): ByteArray {
        return repository.sendCommand(command)
    }
}
