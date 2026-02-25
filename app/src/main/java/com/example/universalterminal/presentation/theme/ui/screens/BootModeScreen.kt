package com.example.universalterminal.presentation.theme.ui.screens

import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.universalterminal.presentation.theme.ui.BootModeViewModel
import com.example.universalterminal.presentation.theme.ui.FirmwareFile
import com.example.universalterminal.presentation.theme.ui.FirmwareType
import com.example.universalterminal.presentation.theme.ui.FirmwareUpdateState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.substringAfter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BootModeScreen(
    onNavigateBack: () -> Unit,
    viewModel: BootModeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val selectedFirmware by viewModel.selectedFirmware.collectAsState()
    val fileValidationError by viewModel.fileValidationError.collectAsState()
    val scope = rememberCoroutineScope()
    val firmwareUpdateState by viewModel.firmwareUpdateState.collectAsState()
    val isUpdateButtonEnabled by viewModel.isUpdateButtonEnabled.collectAsState()

    // Обработка состояний обновления
    LaunchedEffect(firmwareUpdateState) {
        when (val state = firmwareUpdateState) {
            is FirmwareUpdateState.Success -> {
                Toast.makeText(context, "Firmware updated successfully", Toast.LENGTH_SHORT).show()
                delay(2000)
                viewModel.resetUpdateState()
            }
            is FirmwareUpdateState.Error -> {
                Toast.makeText(context, "Firmware update failed: ${state.message}", Toast.LENGTH_LONG).show()
                delay(2000)
                viewModel.resetUpdateState()
            }
            is FirmwareUpdateState.BootError -> {
                Toast.makeText(context, "Boot error: ${state.message}", Toast.LENGTH_LONG).show()
                delay(2000)
                viewModel.resetUpdateState()
            }
            else -> {}
        }
    }


    val singleFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.processFirmwareFile(context, it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Boot Mode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Device Information Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Device Info",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Device Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val model = deviceInfo?.deviceInfo?.model ?: "Unknown"
                    val firmwareVersion = deviceInfo?.deviceInfo?.firmwareVersion.orEmpty()

// Извлекаем hwRev из firmwareVersion
                    val hwRev = firmwareVersion
                        .substringAfter("hw:rev.", "")
                        .substringBefore(" ", "")
                        .trim()

                    val hardwareInfo = if (hwRev.isNotEmpty()) "$model, $hwRev" else model

                    Log.e("check", hardwareInfo)
                    Log.e("check2", hwRev)


                    DeviceInfoRow("Device Name", deviceInfo?.name ?: "Unknown")
                    DeviceInfoRow("Address", deviceInfo?.address ?: "Unknown")
                    DeviceInfoRow(label = "Serial Number", value = deviceInfo?.deviceInfo?.serialNumber?.removePrefix("ser.") ?: "Unknown")
                    DeviceInfoRow("Hardware", hardwareInfo)
                    DeviceInfoRow("Version", deviceInfo?.deviceInfo?.version?.substringAfter("sw:")
                        ?.substringBefore(" ")
                        ?.trim() ?: "Unknown")
                }
            }

            // Firmware Selection
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        "Firmware Update",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            singleFilePickerLauncher.launch("application/zip")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = "Upload Firmware File",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Firmware File (.zip)")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display file validation error if any
                    fileValidationError?.let { error ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Display selected firmware info with custom swipe-to-delete
                    selectedFirmware?.let { firmware ->
                        Text(
                            "Selected Firmware File",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        SwipeableFirmwareCard(
                            firmware = firmware,
                            onDismiss = {
                                viewModel.clearSelectedFirmware()
                            }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.startFirmwareUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isUpdateButtonEnabled && selectedFirmware != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                disabledContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                            )
                        ) {
                            when (firmwareUpdateState) {
                                is FirmwareUpdateState.Updating -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Updating...")
                                }
                                is FirmwareUpdateState.Success -> {
                                    Icon(Icons.Default.Check, contentDescription = "Success")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Update Successful")
                                }
                                is FirmwareUpdateState.Error -> {
                                    Icon(Icons.Default.Error, contentDescription = "Error")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Update Failed")
                                }
                                is FirmwareUpdateState.BootError -> {
                                    Icon(Icons.Default.Error, contentDescription = "Boot Error")
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Boot Error")

                                }
                                else -> Text("Start Firmware Update")
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
fun SwipeableFirmwareCard(firmware: FirmwareFile, onDismiss: () -> Unit) {
    var offsetX by remember { mutableStateOf(0f) }
    val screenWidth = with(LocalDensity.current) { 1000.dp.toPx() }
    val threshold = screenWidth * 0.3f // 30% of screen width

    val draggableState = rememberDraggableState { delta ->
        offsetX += delta
    }

    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        label = "offsetX"
    )

    // Рассчитываем интенсивность красного цвета на основе смещения
    val swipeProgress = (Math.abs(offsetX) / threshold).coerceIn(0f, 1f)
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    val deleteColor = MaterialTheme.colorScheme.error.copy(alpha = swipeProgress * 0.7f)

    // Определяем направление смахивания для отображения соответствующего текста
    val swipeDirection = if (offsetX > 0) "← Смахните вправо" else "Смахните влево →"

    LaunchedEffect(offsetX) {
        if (Math.abs(offsetX) > threshold) {
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Фоновый индикатор смахивания
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(deleteColor),
            contentAlignment = if (offsetX > 0) Alignment.CenterStart else Alignment.CenterEnd
        ) {
            if (swipeProgress > 0.1f) {
                Text(
                    text = "Удалить",
                    color = MaterialTheme.colorScheme.onError,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Основная карточка
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = animatedOffsetX
                    alpha = 1f - (Math.abs(offsetX) / (screenWidth / 2)).coerceIn(0f, 0.2f)
                }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        if (Math.abs(offsetX) <= threshold) {
                            offsetX = 0f
                        }
                    }
                )
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = when (firmware.type) {
                        FirmwareType.NORDIC -> "NORDIC"
                        FirmwareType.WCH -> "WCH"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Version: ${firmware.version}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "File: ${firmware.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Небольшая подсказка для пользователя
                Spacer(modifier = Modifier.height(8.dp))

                if (swipeProgress < 0.1f) {
                    Text(
                        text = swipeDirection,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}
