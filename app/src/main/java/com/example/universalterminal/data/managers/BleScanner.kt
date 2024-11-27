package com.example.universalterminal.data.managers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.util.Log
import com.example.universalterminal.domain.entities.BleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class BleScanner @Inject constructor(private val bleScannerWrapper: BleScannerWrapper,private val scope: CoroutineScope){

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _devicesFlow = MutableStateFlow<List<BleDevice>>(emptyList())
    val devicesFlow = _devicesFlow.asStateFlow()

    private val scannedDevices = mutableListOf<BleDevice>()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device.name
            if(device != null && device.contains("SatelliteOnline")){
                val bleDevice = BleDevice(
                    name = device,
                    address = result.device.address,
                    rssi = result.rssi
                )
                Log.i("BleScanner", "Device found: $device")
                if (!scannedDevices.contains(bleDevice)) {
                    scannedDevices.add(bleDevice)
                    Log.i("BleScanner", "Device added: $device")
                    _devicesFlow.value = scannedDevices.toList()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
        }
    }

    fun startScan() {
        scope.launch {
            try {
                val settings = ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .build()
                bleScannerWrapper.startScan(scanCallback, settings)
                _isScanning.value = true
            } catch (e: Exception) {
                Log.e("BleScanner", "Error starting scan", e)
                _isScanning.value = false
            }
        }
    }

    fun stopScan() {
        scope.launch {
            try {
                bleScannerWrapper.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.e("BleScanner", "Error stopping scan", e)
            } finally {
                _isScanning.value = false
                scannedDevices.clear()
            }
        }
    }
}

class BleScannerWrapper(){
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    @SuppressLint("MissingPermission")
    fun startScan(callback: ScanCallback, settings: ScanSettings) {
        bleScanner?.startScan(null, settings, callback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan(callback: ScanCallback) {
        bleScanner?.stopScan(callback)
    }
}