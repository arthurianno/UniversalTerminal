package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class LoadDfuUseCase @Inject constructor(private val bleRepository: BleRepository) {
    suspend operator fun invoke(address: String, filePath: String) =
        bleRepository.loadDfuFirmware(address, filePath)

}