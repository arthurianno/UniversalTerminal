package com.example.universalterminal.presentation.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import javax.inject.Inject

class MainViewModel @Inject constructor() : ViewModel() {

    val visiblePermissionDialogQueue = mutableStateListOf<String>()



    @SuppressLint("NewApi")
    fun dismissDialog() {
        if (visiblePermissionDialogQueue.isNotEmpty()) {
            visiblePermissionDialogQueue.removeLast()
        }
    }



    fun onPermissionResult(permission: String, isGranted: Boolean) {
        // If not granted and not already in queue, add to queue
        if (!isGranted && !visiblePermissionDialogQueue.contains(permission)) {
            visiblePermissionDialogQueue.add(permission)
        }
        // If granted, remove from queue
        else if (isGranted) {
            visiblePermissionDialogQueue.remove(permission)
        }
    }


    }