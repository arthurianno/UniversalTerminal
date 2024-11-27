package com.example.universalterminal.presentation.theme.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.universalterminal.domain.entities.BleDevice

import com.example.universalterminal.presentation.components.DeviceItem
import com.example.universalterminal.presentation.theme.ui.BleScanViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScanScreen(
    viewModel: BleScanViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectingDevice by viewModel.connectingDevice.collectAsState()
    val connectionInProgress by viewModel.connectionInProgress.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var currentDevice by remember { mutableStateOf<BleDevice?>(null) }
    var pinCode by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BLE Device Scanner") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (isScanning) viewModel.stopScan()
                    else viewModel.startScan()
                },
                icon = {
                    Icon(
                        imageVector = if (isScanning) Icons.Default.Search else Icons.Default.Search,
                        contentDescription = if (isScanning) "Stop Scan" else "Start Scan"
                    )
                },
                text = {
                    Text(if (isScanning) "Stop Scanning" else "Start Scanning")
                },
                containerColor = if (isScanning) Color.Red else MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Search/Filter TextField
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter devices") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Filter") }
            )

            // Devices List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val filteredDevices = devices.filter {
                    it.name!!.contains(searchQuery, ignoreCase = true)
                }

                items(filteredDevices) { device ->
                    DeviceItem(device = device) {
                        currentDevice = device
                        showPinDialog = true
                    }
                }
            }
        }

        // PIN Dialog
        if (showPinDialog && currentDevice != null) {
            AlertDialog(
                onDismissRequest = { showPinDialog = false },
                title = { Text("Connect to ${currentDevice!!.name}") },
                text = {
                    Column {
                        TextField(
                            value = pinCode,
                            onValueChange = { pinCode = it },
                            placeholder = { Text("Enter PIN code") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showPinDialog = false
                        currentDevice?.let { device ->
                            viewModel.connectToDevice(device, pinCode)
                        }
                    }) {
                        Text("Connect")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Connection Progress Dialog
        if (connectionInProgress && connectingDevice != null) {
            AlertDialog(
                onDismissRequest = { /* Prevent dismiss */ },
                title = { Text("Connecting to ${connectingDevice!!.name}") },
                text = {
                    Column {
                        Text("Please wait while we establish the connection...")
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator()
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
    }
}

