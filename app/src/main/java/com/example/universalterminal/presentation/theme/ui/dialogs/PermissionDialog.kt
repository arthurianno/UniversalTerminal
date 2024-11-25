package com.example.universalterminal.presentation.theme.ui.dialogs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
           TextButton(onClick = onOkClick) {
                Text(text = "OK")
            }
        },
        dismissButton = {
            if (isPermanentlyDeclined) {
                TextButton(onClick = onGoToAppSettingsClick) {
                    Text(text = "App Settings")
                }
            }
        },
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

class BluetoothPermissionTextProvider : PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "It seems you permanently declined bluetooth permission. " + "You can go to the app settings to grant it."
        } else{
            "This app needs access to bluetooth in order to scan for bluetooth devices."
        }
    }

}

class LocationPermissionTextProvider : PermissionTextProvider{
    override fun getDescription(isPermanentlyDeclined: Boolean): String {
        return if (isPermanentlyDeclined) {
            "It seems you permanently declined location permission. " + "You can go to the app settings to grant it."
        } else{
            "This app needs access to location in order to scan for bluetooth devices."
        }
    }

}
