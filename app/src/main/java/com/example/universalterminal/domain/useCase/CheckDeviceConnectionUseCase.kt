package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class CheckDeviceConnectionUseCase @Inject constructor(private val bleRepository: BleRepository)  {
    suspend operator fun invoke(): Boolean {
        return bleRepository.isConnected()
    }

}
