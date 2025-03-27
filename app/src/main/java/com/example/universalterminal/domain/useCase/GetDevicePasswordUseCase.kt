package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDevicePasswordUseCase @Inject constructor(private val deviceRepository: DeviceWorkingRepository)  {
    suspend operator fun invoke(deviceAddress: String): Flow<String?> {
        return deviceRepository.getDevicePassword(deviceAddress)
    }

}