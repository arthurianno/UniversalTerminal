package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class ConnectToDeviceUseCase @Inject constructor(private val repository: BleRepository){
    suspend operator fun invoke(bleDevice: BleDevice): Boolean {
        return repository.connectToDevice(bleDevice)
    }
}
