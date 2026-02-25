package com.example.universalterminal.data.managers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.presentation.theme.ui.ScanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class BleScanner @Inject constructor(
    private val bleScannerWrapper: BleScannerWrapper,
    private val context: Context
) {

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _devicesAll = MutableStateFlow<BluetoothDevice?>(null)
    val devicesAll = _devicesAll.asStateFlow()

    private val _devicesFlow = MutableStateFlow<Set<BleDevice>>(emptySet())
    val devicesFlow = _devicesFlow.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private val scannedDevices = mutableListOf<BleDevice>()

    @RequiresApi(Build.VERSION_CODES.O)
    fun startScan(scanMode: ScanMode) {
        if (_isScanning.value) {
            Log.i("BleScanner", "Scan already in progress, ignoring request")
            return
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val hasAllPermissions = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasAllPermissions) {
            Log.e("BleScanner", "Missing required permissions for BLE scanning")
            return
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(when (scanMode) {
                    ScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                    ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                    ScanMode.AGGRESSIVE -> ScanSettings.SCAN_MODE_LOW_LATENCY
                })
                .setReportDelay(0L)
                .setLegacy(true)
                .build()

            val filters = listOf(
                ScanFilter.Builder().build()
            )

            scanCallback = object : ScanCallback() {
                @SuppressLint("MissingPermission")
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val deviceName = result.scanRecord?.deviceName ?: result.device.name ?: "Unknown"
                    if (deviceName != "Unknown") {
                        val bleDevice = BleDevice(
                            name = deviceName,
                            address = result.device.address,
                            rssi = result.rssi,
                            device = result.device
                        )
                        val existingIndex = scannedDevices.indexOfFirst { it.address == bleDevice.address }
                        if (existingIndex >= 0) {
                            scannedDevices[existingIndex] = bleDevice
                        } else {
                            scannedDevices.add(bleDevice)
                        }
                        _devicesFlow.value = scannedDevices.toSet()
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.e("BleScanner", "Scan failed with error code: $errorCode")
                    stopScan()
                }
            }

            bleScannerWrapper.startScan(scanCallback as ScanCallback, settings, filters)
            _isScanning.value = true
            Log.i("BleScanner", "Scan started")
        } catch (e: Exception) {
            Log.e("BleScanner", "Error starting scan", e)
            _isScanning.value = false
        }
    }

    fun stopScan() {
        try {
            scanCallback?.let {
                bleScannerWrapper.stopScan(it)
                Log.i("BleScanner", "Scan stopped successfully")
            } ?: Log.w("BleScanner", "No active scan callback to stop")
            scanCallback = null
        } catch (e: Exception) {
            Log.e("BleScanner", "Error stopping scan", e)
        } finally {
            _isScanning.value = false
            scannedDevices.clear()
            _devicesFlow.value = emptySet()
        }
    }
}

class BleScannerWrapper {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    @SuppressLint("MissingPermission")
    fun startScan(callback: ScanCallback, settings: ScanSettings, filters: List<ScanFilter>?) {
        bleScanner?.startScan(filters, settings, callback)  // Изменил: добавил filters
    }

    @SuppressLint("MissingPermission")
    fun stopScan(callback: ScanCallback) {
        bleScanner?.stopScan(callback)
    }
}
