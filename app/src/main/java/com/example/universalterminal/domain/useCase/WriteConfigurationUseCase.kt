package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WriteConfigurationUseCase @Inject constructor(private val bleRepository: BleRepository) {
    suspend operator fun invoke(command: ByteArray) : Flow<Boolean> {
        return bleRepository.loadConfiguration(command)
    }
}