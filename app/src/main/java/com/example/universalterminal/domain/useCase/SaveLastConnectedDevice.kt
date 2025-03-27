package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import javax.inject.Inject

class SaveLastConnectedDevice @Inject constructor(private val deviceRepository: DeviceWorkingRepository)  {
    suspend operator fun invoke(bleDevice: BleDevice) {
        deviceRepository.saveLastConnectedDevice(bleDevice)
    }

}