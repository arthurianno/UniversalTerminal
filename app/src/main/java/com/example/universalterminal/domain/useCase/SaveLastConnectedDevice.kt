package com.example.universalterminal.domain.useCase

import android.util.Log
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import javax.inject.Inject

class SaveLastConnectedDevice @Inject constructor(private val deviceRepository: DeviceWorkingRepository)  {
    suspend operator fun invoke(bleDevice: BleDevice) {
        Log.i("DebugCheck", "Внутри SaveLastConnectedDevice для устройства: $bleDevice")
        deviceRepository.saveLastConnectedDevice(bleDevice)
    }

}