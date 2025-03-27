package com.example.universalterminal.presentation.theme

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class MainViewModel @Inject constructor() : ViewModel() {

    private val _permissionStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionStatus: StateFlow<Map<String, Boolean>> = _permissionStatus.asStateFlow()

    val visiblePermissionDialogQueue = mutableStateListOf<String>()
    private val _allPermissionsGranted = MutableStateFlow(false)
    val allPermissionsGranted: StateFlow<Boolean> = _allPermissionsGranted.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(true)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(true)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Храним контекст для проверки (будет передан из MainActivity)
    private var appContext: Context? = null

    init {
        // Проверка будет запускаться только после установки контекста
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
        viewModelScope.launch {
            while (true) {
                checkBluetoothAndLocationStatus()
                kotlinx.coroutines.delay(5000) // Проверка каждые 5 секунд
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkBluetoothAndLocationStatus() {
        val context = appContext ?: return // Если контекст не установлен, ничего не делаем

        // Проверка Bluetooth
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val isBluetoothOn = bluetoothAdapter?.isEnabled == true
        _isBluetoothEnabled.value = isBluetoothOn
        Log.d("MainViewModel", "Bluetooth enabled: $isBluetoothOn")

        // Проверка геолокации
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        val isLocationOn = isGpsEnabled || isNetworkEnabled
        _isLocationEnabled.value = isLocationOn
        Log.d("MainViewModel", "GPS enabled: $isGpsEnabled, Network enabled: $isNetworkEnabled, Location on: $isLocationOn")

        // Формирование сообщения об ошибке
        val errors = mutableListOf<String>()
        if (!isBluetoothOn) errors.add("Bluetooth отключен. Пожалуйста, включите Bluetooth.")
        if (!isLocationOn) errors.add("Геолокация отключена. Пожалуйста, включите геолокацию.")

        _errorMessage.value = if (errors.isNotEmpty()) {
            "Упс, что-то пошло не так!\n" + errors.joinToString("\n")
        } else {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun dismissDialog() {
        if (visiblePermissionDialogQueue.isNotEmpty()) {
            visiblePermissionDialogQueue.removeLast()
        }
    }

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        val currentStatus = _permissionStatus.value.toMutableMap()
        currentStatus[permission] = isGranted
        _permissionStatus.value = currentStatus

        if (!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        } else if (isGranted) {
            visiblePermissionDialogQueue.remove(permission)
        }

        _allPermissionsGranted.value = currentStatus.all { it.value }
    }

    fun getDeniedPermissions(): List<String> {
        return _permissionStatus.value
            .filter { !it.value }
            .keys
            .toList()
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}