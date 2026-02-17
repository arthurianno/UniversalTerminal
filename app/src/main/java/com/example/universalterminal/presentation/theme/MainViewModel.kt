package com.example.universalterminal.presentation.theme

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val deviceWorkingRepository: DeviceWorkingRepository
) : ViewModel() {

    private val _allPermissionsGranted = MutableStateFlow(false)
    val allPermissionsGranted: StateFlow<Boolean> = _allPermissionsGranted.asStateFlow()

    private val _visiblePermissionDialogQueue = MutableStateFlow<List<String>>(emptyList())
    val visiblePermissionDialogQueue: StateFlow<List<String>> = _visiblePermissionDialogQueue.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _lastConnectedDevice = MutableStateFlow<BleDevice?>(null)
    val lastConnectedDevice: StateFlow<BleDevice?> = _lastConnectedDevice.asStateFlow()

    private var context: Context? = null

    init {
        viewModelScope.launch {
            deviceWorkingRepository.getLastConnectedDevice().collect { device ->
                Log.e("MainViewModel", "Emitting device23: $device")
                _lastConnectedDevice.value = device
            }
        }
    }

    fun setContext(context: Context) {
        this.context = context
        checkInitialPermissions()
        //refreshLastConnectedDevice()
    }

    fun checkBlePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            checkPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    suspend fun refreshLastConnectedDevice(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val device = deviceWorkingRepository.getLastConnectedDevice().first()
                Log.d("MainViewModel", "Retrieved device: $device")
                _lastConnectedDevice.value = device
                device?.name // Возвращаем имя устройства или null
            } catch (e: Exception) {
                Log.e("MainViewModel", "Error refreshing last connected device", e)
                null
            }
        }
    }


    private fun checkInitialPermissions() {
        context?.let { ctx ->
            val permissionsToCheck = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            checkPermissions(permissionsToCheck)
        } ?: run {
            Log.e("MainViewModel", "Context is null during permission check")
            _errorMessage.value = "Контекст приложения недоступен"
        }
    }

    private fun checkPermissions(permissionsToCheck: Array<String>) {
        context?.let { ctx ->
            val deniedPermissions = permissionsToCheck.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            viewModelScope.launch(Dispatchers.Main) {
                _visiblePermissionDialogQueue.value = deniedPermissions
                _allPermissionsGranted.value = deniedPermissions.isEmpty()
                if (deniedPermissions.isNotEmpty()) {
                    Log.d("MainViewModel", "Permissions denied: $deniedPermissions")
                } else {
                    Log.d("MainViewModel", "All required permissions granted")
                }
            }
        }
    }

    fun onPermissionResult(permission: String, isGranted: Boolean) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentQueue = _visiblePermissionDialogQueue.value.toMutableList()
            if (!isGranted && !currentQueue.contains(permission)) {
                currentQueue.add(permission)
            } else if (isGranted && currentQueue.contains(permission)) {
                currentQueue.remove(permission)
            }
            _visiblePermissionDialogQueue.value = currentQueue
            _allPermissionsGranted.value = currentQueue.isEmpty()
            Log.d("MainViewModel", "Permission $permission granted: $isGranted, queue: $currentQueue")
            if (_allPermissionsGranted.value) {
                //refreshLastConnectedDevice()
            }
        }
    }

    fun dismissDialog(permission: String? = null) {
        viewModelScope.launch(Dispatchers.Main) {
            val currentQueue = _visiblePermissionDialogQueue.value.toMutableList()
            if (permission != null && currentQueue.contains(permission)) {
                currentQueue.remove(permission)
            } else {
                currentQueue.removeLastOrNull()
            }
            _visiblePermissionDialogQueue.value = currentQueue
            _allPermissionsGranted.value = currentQueue.isEmpty()
            Log.d("MainViewModel", "Dialog dismissed, queue: $currentQueue")
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}