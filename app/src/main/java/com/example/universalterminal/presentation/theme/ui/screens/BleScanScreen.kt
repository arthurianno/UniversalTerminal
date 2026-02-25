package com.example.universalterminal.presentation.theme.ui.screens

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.ScanMode
import com.example.universalterminal.presentation.theme.ui.BleScanViewModel
import com.example.universalterminal.presentation.theme.ui.dialogs.DeviceItemEnhanced
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BleScanScreen(
    viewModel: BleScanViewModel = hiltViewModel(),
    onNavigateToConnected: () -> Unit
) {
    val devices by viewModel.devices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val connectingDevice by viewModel.connectingDevice.collectAsState()
    val connectionInProgress by viewModel.connectionInProgress.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }
    var currentDevice by remember { mutableStateOf<BleDevice?>(null) }
    val pinCode by viewModel.currentPin.collectAsState()
    val shouldNavigate by viewModel.navigateToConnected.collectAsState()
    var lastScanTime by remember { mutableStateOf(0L) }
    val debounceTime = 1000L
    val selectedScanMode by viewModel.scanMode.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val pinError by viewModel.pinError.collectAsState()
    var selectedTabIndex by remember { mutableStateOf(0) }
    val context = LocalContext.current

    val bondedDevices = remember(context) {
        derivedStateOf {
            if (!hasBluetoothConnectPermission(context)) {
                return@derivedStateOf emptySet()
            }
            val bluetoothManager =
                context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter?.bondedDevices?.map { device ->
                BleDevice(
                    name = device.name ?: "Unknown Device",
                    address = device.address,
                    rssi = 0,
                    deviceInfo = null
                )
            }?.toSet() ?: emptySet()
        }
    }

    LaunchedEffect(shouldNavigate) {
        if (shouldNavigate) {
            onNavigateToConnected()
            viewModel.onNavigatedToConnected()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetConnectionState()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Text(
                        "Scan Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Scan Mode",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ScanMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedScanMode == mode,
                                onClick = {
                                    viewModel.setScanMode(mode)
                                }
                            )
                            Text(
                                text = mode.name.replace("_", " ").lowercase()
                                    .replaceFirstChar { it.uppercase() },
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "BLE Device Scanner",
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.shadow(4.dp)
                )
            },
            floatingActionButton = {
                if (selectedTabIndex == 0) { // Показываем FAB только на вкладке Scan
                    ExtendedFloatingActionButton(
                        onClick = {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastScanTime >= debounceTime) {
                                if (isScanning) viewModel.stopScan()
                                else viewModel.startScan()
                                lastScanTime = currentTime
                            } else {
                                Log.i("BleScanScreen", "Button clicked too soon, ignoring")
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = if (isScanning) "Stop Scan" else "Start Scan"
                            )
                        },
                        text = {
                            Text(if (isScanning) "Stop Scanning" else "Start Scanning")
                        },
                        containerColor = if (isScanning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp)
                    )
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Вкладки для переключения между Scan и Bonded
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("Scan") }
                    )
                    Tab(
                        selected = selectedTabIndex == 1,
                        onClick = { selectedTabIndex = 1 },
                        text = { Text("Bonded") }
                    )
                }

                // Поле поиска
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filter devices") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shadow(4.dp),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Filter",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                // Отображаем содержимое в зависимости от выбранной вкладки
                when (selectedTabIndex) {
                    0 -> {
                        // Вкладка Scan
                        if (isScanning) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Scanning for devices...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (devices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                                        contentDescription = "No devices",
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No devices found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Start scanning to discover nearby BLE devices",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(rememberNestedScrollInteropConnection()),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val filteredDevices = devices.filter {
                                    it.name?.contains(searchQuery, ignoreCase = true) == true
                                }
                                items(filteredDevices) { device ->
                                    DeviceItemEnhanced(device = device) {
                                        Log.i("BleScanScreen", "Showing PIN dialog for device ${device.address}")
                                        currentDevice = device
                                        showPinDialog = true
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        // Вкладка Bonded
                        if (bondedDevices.value.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bluetooth,
                                        contentDescription = "No bonded devices",
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "No bonded devices found",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Pair devices via Bluetooth settings or scanning to see them here",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val filteredBondedDevices = bondedDevices.value.filter {
                                    it.name.contains(searchQuery, ignoreCase = true)
                                }

                                items(filteredBondedDevices) { device ->
                                    DeviceItemEnhanced(device = device) {
                                        Log.i("BleScanScreen", "Showing PIN dialog for bonded device ${device.address}")
                                        currentDevice = device
                                        showPinDialog = true
                                    }
                                }
                            }
                        }
                    }
                }
            }

            currentDevice?.takeIf { showPinDialog }?.let { dialogDevice ->
                Dialog(onDismissRequest = { showPinDialog = false }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Connect to ${dialogDevice.name}",
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Enter the PIN code to establish connection",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            OutlinedTextField(
                                value = pinCode,
                                onValueChange = { newPin -> viewModel.updatePin(newPin) }, // Явно указываем параметр вместо 'it'
                                placeholder = { Text("PIN code") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                isError = pinError != null // Исправлено: pinError вместо pin error
                            )
                            // Отображаем сообщение об ошибке, если оно есть
                            pinError?.let { error ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showPinDialog = false
                                        viewModel.resetPinError() // Сбрасываем ошибку при отмене
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                ) {
                                    Text("Cancel")
                                }
                                Button(
                                    onClick = {
                                        viewModel.connectToDevice(dialogDevice, pinCode)
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    ),
                                    enabled = !connectionInProgress && pinCode.isNotBlank() // Отключаем, если PIN пустой
                                ) {
                                    Text("Connect")
                                }
                            }
                        }
                    }
                }
            }

            connectingDevice?.takeIf { connectionInProgress }?.let { activeDevice ->
                Dialog(onDismissRequest = { /* Prevent dismiss */ }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                "Connecting to ${activeDevice.name}",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Please wait while we establish the connection...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 24.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hasBluetoothConnectPermission(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
}
