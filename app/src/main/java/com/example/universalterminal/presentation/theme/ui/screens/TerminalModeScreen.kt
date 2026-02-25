package com.example.universalterminal.presentation.theme.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.universalterminal.presentation.theme.ui.TerminalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalModeScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val terminalState by viewModel.terminalState.collectAsState()
    var commandInput by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }
    val device by viewModel.deviceInfo.collectAsState()
    val scrollState = rememberScrollState()

    val availableCommands = listOf(
        "gettime", "time", "settime.", "rd.", "version", "battery",
        "serial", "setser.", "find", "mac", "erase", "boot", "reset", "setraw"
    )
    val runAvailableCommands = listOf(
        "gettime", "time", "rd.", "version", "battery",
        "serial", "find", "mac", "erase", "reset",
    )

    val filteredCommands = availableCommands.filter {
        it.startsWith(commandInput, ignoreCase = true) && commandInput.isNotEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Terminal Mode",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Text("Run All Available Commands")
                            }
                        },
                        state = rememberTooltipState()
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    device?.let { device ->
                                        for (command in runAvailableCommands) {
                                            viewModel.sendTerminalCommand(device, command)
                                            delay(100)
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .height(36.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Тест команд",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(scrollState) // Добавляем прокрутку
                .imePadding() // Учитываем высоту клавиатуры
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Terminal Output с кнопкой очистки
            Card(
                modifier = Modifier
                    .weight(1f, fill = false) // Убираем жёсткое растягивание
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .padding(bottom = 40.dp)
                    ) {
                        items(terminalState.responses) { response ->
                            Text(
                                text = response,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { viewModel.clearTerminal() },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Terminal",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Input Section
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = {
                            commandInput = it
                            showDropdown = it.isNotEmpty() && filteredCommands.isNotEmpty()
                        },
                        label = { Text("Enter command") },
                        modifier = Modifier
                            .weight(1f)
                            .background(Color.White, RoundedCornerShape(8.dp)),
                        trailingIcon = {
                            Row {
                                if (commandInput.isNotEmpty()) {
                                    IconButton(onClick = { commandInput = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (commandInput.isNotEmpty()) {
                                            scope.launch {
                                                device?.let {
                                                    viewModel.sendTerminalCommand(it, commandInput)
                                                }
                                            }
                                        }
                                    },
                                    enabled = commandInput.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (commandInput.isNotEmpty())
                                            MaterialTheme.colorScheme.primary
                                        else Color.Gray
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary,
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                // Dropdown with command suggestions
                if (showDropdown && filteredCommands.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .heightIn(max = 200.dp)
                        ) {
                            items(filteredCommands) { command ->
                                Text(
                                    text = command,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            commandInput = command
                                            showDropdown = false
                                        }
                                        .padding(8.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Добавляем Spacer для дополнительного пространства под клавиатурой
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Прокручиваем к полю ввода, когда клавиатура открыта
    LaunchedEffect(commandInput) {
        if (commandInput.isNotEmpty()) {
            scope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
}
