package com.example.universalterminal.presentation.theme.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
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
import org.openjdk.javax.tools.Tool

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
    var showConfirmDialog by remember { mutableStateOf(false) }
    val device by viewModel.deviceInfo.collectAsState()

    // List of available commands from Table 1
    val availableCommands = listOf(
        "gettime", "time", "settime.", "rd.", "version", "battery",
        "serial", "setser.", "find", "mac", "erase", "boot", "reset", "setraw"
    )
    val runAvailableCommands = listOf(
        "gettime", "time", "rd.", "version", "battery",
        "serial", "find", "mac", "erase", "reset",
    )

    // Filtered commands based on input
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
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

    ) {  paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Terminal Output
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
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
            }

            // Input Section with Run All Commands Button
            Column(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Command Input Field
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
                                // Clear Icon
                                if (commandInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { commandInput = "" },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                // Send Icon
                                IconButton(
                                    onClick = { if (commandInput.isNotEmpty()) showConfirmDialog = true },
                                    enabled = commandInput.isNotEmpty()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
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

                    // Run All Commands Button
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ) {
                                Text(
                                    text = "Тест всех команд",
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        state = rememberTooltipState()
                    )
                    {

                    IconButton(
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
                            .padding(start = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Run All Commands",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
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
        }

        // Confirmation Dialog
        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Confirm Command") },
                text = { Text("Send command: $commandInput?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                device?.let { viewModel.sendTerminalCommand(it, commandInput) }
                            }
                            showConfirmDialog = false
                        }
                    ) {
                        Text("Send")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
            }
        }
    }
}