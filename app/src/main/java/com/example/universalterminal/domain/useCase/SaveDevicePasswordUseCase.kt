package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import javax.inject.Inject

class SaveDevicePasswordUseCase @Inject constructor(private val deviceRepository: DeviceWorkingRepository)  {
    suspend operator fun invoke(deviceAddress: String, password: String) {
        deviceRepository.saveDevicePassword(deviceAddress, password)
    }
}