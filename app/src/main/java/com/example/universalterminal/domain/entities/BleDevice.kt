package com.example.universalterminal.domain.entities

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val deviceInfo: DeviceInfo? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleDevice) return false
        return address == other.address
    }

    override fun hashCode(): Int {
        return address.hashCode()
    }
}

data class DeviceInfo(
    var version : String? = null,
    var serialNumber : String? = null,
    var model : String? = null,
    val firmwareVersion : String? = null,
)
