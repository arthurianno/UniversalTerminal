package com.example.universalterminal.data.managers

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.DeviceInfo
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceWorkingRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceWorkingRepository {
    private val TAG = "DeviceRepository"

    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val bluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    companion object {
        private const val PREFS_NAME = "device_preferences"
        private const val KEY_LAST_DEVICE_ADDRESS = "last_device_address"
        private const val KEY_LAST_DEVICE_NAME = "last_device_name"
        private const val KEY_LAST_DEVICE_RSSI = "last_device_rssi"
        private const val KEY_PASSWORD_PREFIX = "password_"
        private const val KEY_DEVICE_INFO_VERSION = "device_info_version"
        private const val KEY_DEVICE_INFO_SERIAL_NUMBER = "device_info_serial_number"
        private const val KEY_DEVICE_INFO_MODEL = "device_info_model"
        private const val KEY_DEVICE_INFO_FIRMWARE_VERSION = "device_info_firmware_version"
        private const val KEY_DEVICE_INFO_HARDWARE_VERSION = "device_info_hardware_version"
    }

    override suspend fun saveLastConnectedDevice(device: BleDevice): Unit =
        withContext(Dispatchers.IO) {
            try {
                sharedPreferences.edit().apply {
                    putString(KEY_LAST_DEVICE_ADDRESS, device.address)
                    putString(KEY_LAST_DEVICE_NAME, device.name)
                    putInt(KEY_LAST_DEVICE_RSSI, device.rssi)
                    apply()
                }
                Log.d(
                    TAG,
                    "Saved last connected device: ${device.name}, ${device.address}, RSSI: ${device.rssi}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error saving last connected device", e)
            }
        }

    override suspend fun getDeviceInformation(device: BleDevice): Flow<BleDevice?> =
        flow {
            val result = withContext(Dispatchers.IO) {
                try {
                    val deviceAddress = device.address
                    val version = sharedPreferences.getString("${KEY_DEVICE_INFO_VERSION}_$deviceAddress", null)
                    val serialNumber = sharedPreferences.getString("${KEY_DEVICE_INFO_SERIAL_NUMBER}_$deviceAddress", null)
                    val model = sharedPreferences.getString("${KEY_DEVICE_INFO_MODEL}_$deviceAddress", null)
                    val firmwareVersion = sharedPreferences.getString("${KEY_DEVICE_INFO_FIRMWARE_VERSION}_$deviceAddress", null)

                    val deviceInfo = DeviceInfo(
                        version = version,
                        serialNumber = serialNumber,
                        model = model,
                        firmwareVersion = firmwareVersion,
                    )

                    device.copy(deviceInfo = deviceInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting device information", e)
                    null
                }
            }
            emit(result)
        }




    override suspend fun getLastConnectedDevice(): Flow<BleDevice?> =
        flow {
            val result = withContext(Dispatchers.IO) {
                try {
                    val address = sharedPreferences.getString(KEY_LAST_DEVICE_ADDRESS, null)
                    val name = sharedPreferences.getString(KEY_LAST_DEVICE_NAME, null)
                    val rssi = sharedPreferences.getInt(KEY_LAST_DEVICE_RSSI, -100)

                    if (address != null && name != null) {
                        Log.d(TAG, "Retrieved last connected device: $name, $address, RSSI: $rssi")

                        try {
                            // Get the actual BluetoothDevice from the adapter
                            val device = bluetoothAdapter.getRemoteDevice(address)
                            BleDevice(
                                name = name,
                                address = address,
                                rssi = rssi,
                                device = device
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error retrieving Bluetooth device", e)
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting last connected device", e)
                    null
                }
            }
            emit(result)
        }


    override suspend fun saveDeviceInfo(device: BleDevice): Unit =
        withContext(Dispatchers.IO) {
            try {
                val deviceAddress = device.address
                sharedPreferences.edit().apply {
                    device.deviceInfo?.let { info ->
                        putString("${KEY_DEVICE_INFO_VERSION}_$deviceAddress", info.version)
                        putString("${KEY_DEVICE_INFO_SERIAL_NUMBER}_$deviceAddress", info.serialNumber)
                        putString("${KEY_DEVICE_INFO_MODEL}_$deviceAddress", info.model)
                        putString("${KEY_DEVICE_INFO_FIRMWARE_VERSION}_$deviceAddress", info.firmwareVersion)

                    }
                    apply()
                }
                Log.d(TAG, "Saved device info: ${device.deviceInfo} for device: ${device.address}")
                val deviceInfo = getDeviceInformation(device)
                Log.d(TAG, "Saved device info: ${deviceInfo.first()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving device info", e)
            }
        }

    override suspend fun saveDevicePassword(deviceAddress: String, password: String): Unit =
        withContext(Dispatchers.IO) {
            try {
                sharedPreferences.edit().apply {
                    putString("$KEY_PASSWORD_PREFIX$deviceAddress", password)
                    apply()
                }
                Log.d(TAG, "Saved password '$password' for device: $deviceAddress")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving device password", e)
            }
        }

    override suspend fun getDevicePassword(deviceAddress: String): Flow<String?> =
        flow {
            val result = withContext(Dispatchers.IO) {
                try {
                    sharedPreferences.getString(
                        "$KEY_PASSWORD_PREFIX$deviceAddress",
                        null
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting device password", e)
                    null
                }
            }
            emit(result)
        }


}



