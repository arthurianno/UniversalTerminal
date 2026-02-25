package com.example.universalterminal.data.managers

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
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
        createSecurePreferences()
    }

    private val bluetoothAdapter by lazy {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothManager?.adapter
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

    private fun createSecurePreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Falling back to regular SharedPreferences", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    override suspend fun saveLastConnectedDevice(device: BleDevice): Unit = withContext(Dispatchers.IO) {
        try {
            Log.e("Check", "Entering saveLastConnectedDevice for device: $device")
            clearDeviceData() // Очистка перед сохранением
            sharedPreferences.edit().apply {
                putString(KEY_LAST_DEVICE_ADDRESS, device.address)
                putString(KEY_LAST_DEVICE_NAME, device.name)
                putInt(KEY_LAST_DEVICE_RSSI, device.rssi)
                commit() // Используем commit() для синхронной записи
            }
            Log.d(TAG, "Saved device: name=${device.name}, address=${device.address}, rssi=${device.rssi}")
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

                    if (version == null && serialNumber == null && model == null && firmwareVersion == null) {
                        // Если информация отсутствует, возвращаем устройство без deviceInfo
                        device.copy(deviceInfo = null)
                    } else {
                        val deviceInfo = DeviceInfo(
                            version = version,
                            serialNumber = serialNumber,
                            model = model,
                            firmwareVersion = firmwareVersion,
                        )
                        device.copy(deviceInfo = deviceInfo)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting device information", e)
                    device.copy(deviceInfo = null)
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
                            val adapter = bluetoothAdapter
                            if (adapter == null) {
                                Log.w(TAG, "Bluetooth adapter is unavailable")
                                return@withContext null
                            }
                            adapter.getRemoteDevice(address)
                            BleDevice(
                                name = name,
                                address = address,
                                rssi = rssi
                            )
                        } catch (e: IllegalArgumentException) {
                            // Если адрес недействителен или устройство не найдено
                            clearDeviceData()
                            Log.e(TAG, "Invalid device address or device not found, cleared data", e)
                            null
                        }
                    } else {
                        Log.d(TAG, "No last connected device found in SharedPreferences")
                        null
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting last connected device", e)
                    clearDeviceData() // Очищаем данные при любой ошибке
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

    override suspend fun clearDeviceData(): Unit = withContext(Dispatchers.IO) {
        try {
            Log.e(TAG, "Clearing all device data from SharedPreferences")
            val lastDeviceAddress = sharedPreferences.getString(KEY_LAST_DEVICE_ADDRESS, null)
            sharedPreferences.edit().apply {
                remove(KEY_LAST_DEVICE_ADDRESS)
                remove(KEY_LAST_DEVICE_NAME)
                remove(KEY_LAST_DEVICE_RSSI)
                lastDeviceAddress?.let { address ->
                    remove("${KEY_DEVICE_INFO_VERSION}_$address")
                    remove("${KEY_DEVICE_INFO_SERIAL_NUMBER}_$address")
                    remove("${KEY_DEVICE_INFO_MODEL}_$address")
                    remove("${KEY_DEVICE_INFO_FIRMWARE_VERSION}_$address")
                    remove("${KEY_DEVICE_INFO_HARDWARE_VERSION}_$address")
                    remove("$KEY_PASSWORD_PREFIX$address")
                }
                commit() // Замените apply() на commit() для синхронной очистки
            }
            Log.d(TAG, "Cleared all device data from SharedPreferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing device data", e)
        }
    }
}
