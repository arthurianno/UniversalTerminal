package com.example.universalterminal.presentation.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.universalterminal.presentation.theme.ui.dialogs.BluetoothPermissionTextProvider
import com.example.universalterminal.presentation.theme.ui.dialogs.LocationPermissionTextProvider
import com.example.universalterminal.presentation.theme.ui.dialogs.PermissionDialog
import com.example.universalterminal.presentation.theme.ui.screens.BleScanScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlin.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @RequiresApi(Build.VERSION_CODES.S)
    private val permissionToRequest = arrayOf(
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.BLUETOOTH_SCAN,
        android.Manifest.permission.BLUETOOTH_ADMIN
    )
    @SuppressLint("NewApi")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversalTerminalTheme {
                val viewModel: MainViewModel by viewModels()
                val dialogQueue = viewModel.visiblePermissionDialogQueue
                val allPermissionsGranted = viewModel.allPermissionsGranted.collectAsState()

                val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { perms ->
                        perms.forEach { (permission, isGranted) ->
                            viewModel.onPermissionResult(
                                permission = permission,
                                isGranted = isGranted
                            )
                        }
                    }
                )

                LaunchedEffect(Unit) {
                    multiplePermissionResultLauncher.launch(permissionToRequest)
                }
                if (allPermissionsGranted.value == true) {
                    BleScanScreen()
                } else {
                    Log.e("MainActivity", "Permissions not granted {${allPermissionsGranted.value}}")
                    dialogQueue
                        .reversed()
                        .forEach { permission ->
                            if (!viewModel.visiblePermissionDialogQueue.contains(permission)) return@forEach

                            PermissionDialog(
                                permissionTextProvider = when (permission) {
                                    android.Manifest.permission.BLUETOOTH_CONNECT -> BluetoothPermissionTextProvider()
                                    android.Manifest.permission.ACCESS_FINE_LOCATION -> LocationPermissionTextProvider()
                                    else -> return@forEach
                                },
                                isPermanentlyDeclined = !shouldShowRequestPermissionRationale(permission),
                                onDismiss = {
                                    viewModel.dismissDialog()
                                },
                                onOkClick = {
                                    viewModel.dismissDialog()
                                    multiplePermissionResultLauncher.launch(arrayOf(permission))
                                },
                                onGoToAppSettingsClick = ::openAppSettings
                            )
                        }
                }
            }
        }
    }
}


fun Activity.openAppSettings(){
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
}
