package com.example.universalterminal.presentation.theme

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.universalterminal.presentation.theme.ui.dialogs.BluetoothPermissionTextProvider
import com.example.universalterminal.presentation.theme.ui.dialogs.LocationPermissionTextProvider
import com.example.universalterminal.presentation.theme.ui.dialogs.PermissionDialog
import com.example.universalterminal.presentation.theme.ui.screens.BleScanScreen
import com.example.universalterminal.presentation.theme.ui.screens.BootModeScreen
import com.example.universalterminal.presentation.theme.ui.screens.ConnectedDeviceScreen
import com.example.universalterminal.presentation.theme.ui.screens.RawModeScreen
import com.example.universalterminal.presentation.theme.ui.screens.TerminalModeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniversalTerminalTheme {
                val viewModel: MainViewModel by viewModels()
                val dialogQueue = viewModel.visiblePermissionDialogQueue.collectAsState()
                val allPermissionsGranted = viewModel.allPermissionsGranted.collectAsState()
                val errorMessage = viewModel.errorMessage.collectAsState()
                val lastConnectedDevice = viewModel.lastConnectedDevice.collectAsState()
                val navController = rememberNavController()

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
                    withContext(Dispatchers.IO) {
                        viewModel.setContext(this@MainActivity)
                    }
                }

                NavHost(navController = navController, startDestination = "scan") {
                    composable("scan") {
                        BleScanScreen(
                            onNavigateToConnected = {
                                viewModel.checkBlePermissions()
                                if (viewModel.allPermissionsGranted.value) {
                                    navController.navigate("connected")
                                } else {
                                    multiplePermissionResultLauncher.launch(permissionToRequest)
                                }
                            }
                        )
                    }
                    composable("connected") {
                        ConnectedDeviceScreen(
                            viewModel = viewModel,  // Передаём viewModel
                            onNavigate = { route ->
                                navController.navigate(route)
                            }
                        )
                    }
                    composable("boot_mode") {
                        BootModeScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }
                    composable("raw_mode") {
                        RawModeScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }
                    composable("terminal_mode") {
                        TerminalModeScreen(
                            onNavigateBack = {
                                navController.navigateUp()
                            }
                        )
                    }
                }

                dialogQueue.value.takeIf { it.isNotEmpty() }?.let {
                    PermissionPrompt(
                        dialogQueue = it,
                        viewModel = viewModel,
                        multiplePermissionResultLauncher = multiplePermissionResultLauncher,
                        openAppSettings = ::openAppSettings,
                        shouldShowRequestPermissionRationale = { permission ->
                            shouldShowRequestPermissionRationale(permission)
                        }
                    )
                }

                errorMessage.value?.let { message ->
                    ErrorDialog(
                        message = message,
                        onDismiss = { viewModel.clearErrorMessage() },
                        onGoToSettings = {
                            viewModel.clearErrorMessage()
                            openSystemSettings()
                        }
                    )
                }
            }
        }
    }

    private fun openAppSettings() {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).also(::startActivity)
    }

    private fun openSystemSettings() {
        Intent(Settings.ACTION_SETTINGS).also(::startActivity)
    }
}

@Composable
fun PermissionPrompt(
    dialogQueue: List<String>,
    viewModel: MainViewModel,
    multiplePermissionResultLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    openAppSettings: () -> Unit,
    shouldShowRequestPermissionRationale: (String) -> Boolean
) {
    dialogQueue
        .reversed()
        .forEach { permission ->
            PermissionDialog(
                permissionTextProvider = when (permission) {
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT -> BluetoothPermissionTextProvider()
                    Manifest.permission.ACCESS_FINE_LOCATION -> LocationPermissionTextProvider()
                    else -> return@forEach
                },
                isPermanentlyDeclined = !shouldShowRequestPermissionRationale(permission),
                onDismiss = { viewModel.dismissDialog(permission) },
                onOkClick = {
                    viewModel.dismissDialog(permission)
                    multiplePermissionResultLauncher.launch(arrayOf(permission))
                },
                onGoToAppSettingsClick = {
                    viewModel.dismissDialog(permission)
                    openAppSettings()
                }
            )
        }
}

@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ошибка") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text("Настройки")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}