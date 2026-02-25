package com.example.universalterminal.presentation.theme.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.universalterminal.presentation.theme.ui.RawModeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RawModeScreen(
    onNavigateBack: () -> Unit,
    viewModel: RawModeViewModel = hiltViewModel()
) {
    var responseData by remember { mutableStateOf<List<ConfigData>>(emptyList()) }
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()
    var scrollToIndex by remember { mutableStateOf<Int?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime // Получаем информацию о клавиатуре

    LaunchedEffect(scrollToIndex) {
        scrollToIndex?.let { index ->
            lazyListState.animateScrollToItem(index, scrollOffset = -100)
            scrollToIndex = null
        }
    }

    LaunchedEffect(Unit) {
        viewModel.readResponseFlow.collectLatest { data ->
            responseData = data
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                actionLabel = "OK",
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Raw Mode") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { viewModel.tryingToSendCommands(forModify = false) },
                    modifier = Modifier
                        .height(56.dp)
                        .width(200.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text(text = "Read All Config")
                    }
                }

                if (responseData.isNotEmpty() && responseData.any { it.isModified }) {
                    Button(
                        onClick = { viewModel.tryingToSendCommands(forModify = true, modifiedData = responseData) },
                        modifier = Modifier
                            .height(56.dp)
                            .width(200.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(text = "Apply Changes")
                        }
                    }
                }

                if (responseData.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = with(density) { imeInsets.getBottom(density).toDp() }), // Динамический отступ для клавиатуры
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        state = lazyListState
                    ) {
                        itemsIndexed(responseData) { index, data ->
                            ConfigCard(
                                data = data,
                                onValueChange = { newValue ->
                                    val currentItem = responseData.find { it.address == data.address }!!
                                    val wasNotModified = !currentItem.isModified
                                    val updatedData = responseData.map {
                                        if (it.address == data.address) it.copy(newValue = newValue) else it
                                    }
                                    responseData = updatedData
                                    val isNowModified = newValue != currentItem.value
                                    if (wasNotModified && isNowModified) {
                                        scrollToIndex = index
                                    }
                                },
                                index = index,
                                lazyListState = lazyListState
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun ConfigCard(
    data: ConfigData,
    onValueChange: (String) -> Unit,
    index: Int,
    lazyListState: LazyListState
) {
    var isError by rememberSaveable { mutableStateOf(false) }
    var isFocused by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    LaunchedEffect(isError) {
        if (isError) {
            delay(500)
            isError = false
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            keyboardController?.show()
            delay(300) // Ждем, пока клавиатура появится
            // Прокручиваем к элементу с учетом высоты клавиатуры
            val keyboardHeight = with(density) { imeInsets.getBottom(density).toDp() }.value.toInt()
            lazyListState.animateScrollToItem(
                index = index,
                scrollOffset = -keyboardHeight - 100 // Отступ для клавиатуры + дополнительный отступ сверху
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (data.isModified) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = data.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = data.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (data.isEditable) {
                var textValue by rememberSaveable {
                    mutableStateOf(
                        when (data.type) {
                            "UINT32_HEX" -> data.newValue ?: data.value.removePrefix("0x").toUInt(16).toString()
                            "TIMESTAMP" -> data.newValue ?: data.value
                            "BITWISE" -> data.newValue ?: data.value
                            else -> data.newValue ?: data.value
                        }
                    )
                }

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { newText ->
                        if (data.name == "Серийный номер устройства" && newText.length > 11) {
                            isError = true
                        } else {
                            textValue = newText
                            onValueChange(newText)
                        }
                    },
                    label = {
                        Text(
                            when (data.type) {
                                "UINT32_HEX" -> "Value (decimal)"
                                "TIMESTAMP" -> "Value (yyyy-MM-dd HH:mm:ss)"
                                "BITWISE" -> "Value (32-bit binary)"
                                "Char[]" -> if (data.name == "Серийный номер устройства") "Value (max 11 chars)" else "Value"
                                else -> "Value"
                            }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                        },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    enabled = data.isEditable,
                    isError = isError,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = when (data.type) {
                            "FLOAT" -> KeyboardType.Decimal
                            "UINT32", "UINT32_HEX", "INT32" -> KeyboardType.Number
                            "TIMESTAMP" -> KeyboardType.Text
                            "BITWISE" -> KeyboardType.Text
                            else -> KeyboardType.Text
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        errorLabelColor = MaterialTheme.colorScheme.error
                    ),
                    interactionSource = interactionSource
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Value:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = data.value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Type",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Type: ${data.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (data.isModified) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Modified",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
data class ConfigData(
    val name: String,
    val address: String,
    val value: String,
    val type: String,
    val isEditable: Boolean = true,
    var newValue: String? = null
) {
    val isModified: Boolean
        get() = newValue != null && newValue != value
}
