package com.example.universalterminal.presentation.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

class MainViewModel @Inject constructor() : ViewModel() {

    private val _permissionStatus = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionStatus: StateFlow<Map<String, Boolean>> = _permissionStatus.asStateFlow()

    val visiblePermissionDialogQueue = mutableStateListOf<String>()
    private val _allPermissionsGranted = MutableStateFlow(false)
    val allPermissionsGranted: StateFlow<Boolean> = _allPermissionsGranted.asStateFlow()


    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun dismissDialog() {
        if (visiblePermissionDialogQueue.isNotEmpty()) {
            visiblePermissionDialogQueue.removeLast()
        }
    }



    fun onPermissionResult(permission: String, isGranted: Boolean) {
        // Update the permission status map
        val currentStatus = _permissionStatus.value.toMutableMap()
        currentStatus[permission] = isGranted
        _permissionStatus.value = currentStatus

        if (!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        } else if (isGranted) {
            visiblePermissionDialogQueue.remove(permission)
        }

        // Check if all permissions are granted
        _allPermissionsGranted.value = currentStatus.all { it.value }
    }

    fun getDeniedPermissions(): List<String> {
        return _permissionStatus.value
            .filter { !it.value }
            .keys
            .toList()
    }


}