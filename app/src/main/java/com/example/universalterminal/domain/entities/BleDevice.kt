package com.example.universalterminal.domain.entities

data class BleDevice(
    val name: String?,
    val address: String,
    val rssi: Int
)