package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class ProcessZipUseCase @Inject constructor(private val bleRepository: BleRepository) {
    suspend operator fun invoke(zipPath: String) = bleRepository.processZipFiles(zipPath)
}