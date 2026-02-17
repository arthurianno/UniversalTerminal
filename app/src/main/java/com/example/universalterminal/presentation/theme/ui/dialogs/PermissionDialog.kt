package com.example.universalterminal.presentation.theme.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun PermissionDialog(
    permissionTextProvider: PermissionTextProvider,
    isPermanentlyDeclined: Boolean,
    onDismiss: () -> Unit,
    onOkClick: () -> Unit,
    onGoToAppSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (!isPermanentlyDeclined) {
                    onOkClick()
                } else {
                    onGoToAppSettingsClick()
                }
            }) {
                Text(text = if (isPermanentlyDeclined) "Настройки приложения" else "ОК")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        title = { Text("Требуется разрешение") },
        text = {
            Text(
                text = permissionTextProvider.getDescription(isPermanentlyDeclined)
            )
        },
        modifier = modifier
    )
}

interface PermissionTextProvider {
    fun getDescription(isPermanentlyDeclined: Boolean): String
}

class BluetoothPermissionTextProvider : PermissionTextProvider {
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "Вы отклонили разрешение на Bluetooth. Перейдите в настройки приложения, чтобы включить его."
        } else {
            "Приложению требуется доступ к Bluetooth для поиска устройств."
        }
    }
}

class LocationPermissionTextProvider : PermissionTextProvider {
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "Вы отклонили разрешение на доступ к местоположению. Перейдите в настройки приложения, чтобы включить его."
        } else {
            "Приложению требуется доступ к местоположению для сканирования Bluetooth-устройств."
        }
    }
}