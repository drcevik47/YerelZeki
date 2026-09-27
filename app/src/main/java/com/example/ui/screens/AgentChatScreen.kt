package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.AgentMessageEntity
import com.example.domain.agent.AgentStepEvent
import com.example.ui.AgentViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentChatScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val currentStepEvent by viewModel.currentStepEvent.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Model & Status Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (modelStatus?.isReady == true) EmeraldSuccess else AmberWarning)
                    )
                    Column {
                        Text(
                            text = modelStatus?.modelName ?: "Gemma 3n Agent",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRunning) "Agent Görev Yürütüyor..." else (modelStatus?.details ?: "Hazır"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.createNewSession("Yeni Görev") },
                    modifier = Modifier.testTag("new_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddComment,
                        contentDescription = "Yeni Sohbet",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Live Step Progress Banner (When running)
        AnimatedVisibility(visible = isRunning) {
            LiveStepBanner(
                stepEvent = currentStepEvent,
                onCancel = { viewModel.cancelExecution() }
            )
        }

        // Messages Feed
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyStateCard(
                        onSelectPrompt = { prompt ->
                            inputPrompt = prompt
                        }
                    )
                }
            }

            items(messages, key = { it.id }) { message ->
                MessageItemCard(
                    message = message,
                    onOpenCode = { path ->
                        viewModel.openFileInEditor(path)
                    }
                )
            }
        }

        // Quick Suggestion Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SuggestionChip(
                onClick = { inputPrompt = "demo_project klasöründeki dosyaları listele ve açıkla" },
                label = { Text("📂 Dosyaları Listele", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { inputPrompt = "demo_project/index.html dosyasını oku ve analiz et" },
                label = { Text("📄 index.html Oku", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { inputPrompt = "demo_project/snake.html dosyasında oynanabilir renkli bir Yılan oyunu oluştur" },
                label = { Text("🎮 Snake Oyunu Yaz", fontSize = 12.sp) }
            )
            SuggestionChip(
                onClick = { inputPrompt = "1'den 100'e kadar asal sayıları hesaplayan bir javascript kodu çalıştır" },
                label = { Text("⚡ JS Hesapla", fontSize = 12.sp) }
            )
        }

        // Input Field
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = {
                        Text(
                            "Agent'a bir görev verin (örn: dosya oluştur, kodla)...",
                            fontSize = 14.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("prompt_input_field"),
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                FilledIconButton(
                    onClick = {
                        val text = inputPrompt.trim()
                        if (text.isNotEmpty() && !isRunning) {
                            inputPrompt = ""
                            viewModel.runGoal(text)
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.size)
                            }
                        }
                    },
                    enabled = inputPrompt.isNotBlank() && !isRunning,
                    modifier = Modifier.size(48.dp).testTag("send_prompt_button")
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Gönder"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveStepBanner(
    stepEvent: AgentStepEvent,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Column {
                    val title = when (stepEvent) {
                        is AgentStepEvent.Thinking -> "Adım ${stepEvent.step}/${stepEvent.maxSteps}: Düşünce Süreci"
                        is AgentStepEvent.ExecutingTool -> "Adım ${stepEvent.step}: ${stepEvent.toolName} Çalıştırılıyor"
                        is AgentStepEvent.ToolDone -> "Adım ${stepEvent.step}: ${stepEvent.toolName} Tamamlandı"
                        is AgentStepEvent.Finished -> "Görev Tamamlandı"
                        is AgentStepEvent.Error -> "Hata Oluştu"
                        AgentStepEvent.Idle -> "Hazır"
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val detail = when (stepEvent) {
                        is AgentStepEvent.ExecutingTool -> stepEvent.args.entries.joinToString(", ") { "${it.key}: ${it.value.take(20)}" }
                        is AgentStepEvent.Thinking -> "Gemma 3n bir sonraki adımı planlıyor..."
                        else -> ""
                    }
                    if (detail.isNotEmpty()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }
                }
            }

            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Durdur",
                    tint = RoseError
                )
            }
        }
    }
}

@Composable
fun MessageItemCard(
    message: AgentMessageEntity,
    onOpenCode: (String) -> Unit
) {
    if (message.sender == "user") {
        // User Bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Text(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    } else {
        // Agent Message Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = "Agent",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Gemma Agent",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CyberCyan
                    )
                }

                // Collapsible Thought Accordion
                if (!message.thought.isNullOrBlank()) {
                    var isThoughtExpanded by remember { mutableStateOf(false) }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isThoughtExpanded = !isThoughtExpanded }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp)
                                .animateContentSize()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Psychology,
                                        contentDescription = "Düşünce",
                                        tint = NeonPurple,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Agent Düşünce Süreci",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = NeonPurple
                                    )
                                }
                                Icon(
                                    imageVector = if (isThoughtExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Genişlet",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isThoughtExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = message.thought,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Tool Execution Pill / Card
                if (!message.toolCallName.isNullOrBlank()) {
                    ToolExecutionCard(
                        toolName = message.toolCallName,
                        argsJson = message.toolCallArgs ?: "{}",
                        result = message.toolResult ?: "",
                        isSuccess = message.status == "SUCCESS",
                        onOpenCode = onOpenCode
                    )
                }

                // Final text content
                if (message.content.isNotBlank() && message.content != message.thought) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ToolExecutionCard(
    toolName: String,
    argsJson: String,
    result: String,
    isSuccess: Boolean,
    onOpenCode: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = DarkCodeBg,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSuccess) EmeraldSuccess.copy(alpha = 0.4f) else RoseError.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val icon = when (toolName.lowercase()) {
                        "write_file" -> Icons.Default.EditNote
                        "read_file" -> Icons.Default.MenuBook
                        "edit_file" -> Icons.Default.FindReplace
                        "execute_code" -> Icons.Default.Terminal
                        "list_directory" -> Icons.Default.FolderOpen
                        else -> Icons.Default.Build
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = toolName,
                        tint = if (isSuccess) EmeraldSuccess else RoseError,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Araç: $toolName",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isSuccess) EmeraldSuccess else RoseError
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Detay",
                    tint = TextSecondaryDark,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))

                // Arguments
                Text(
                    text = "Girdi Parametreleri:",
                    fontSize = 11.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = argsJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = CyberCyan,
                        modifier = Modifier.padding(6.dp)
                    )
                }

                // Output Result
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Çıktı / Gözlem:",
                    fontSize = 11.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(
                        text = result.take(500) + if (result.length > 500) "\n...(kısaltıldı)" else "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = if (isSuccess) Color(0xFFE2E8F0) else RoseError,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard(onSelectPrompt: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = "Terminal",
                tint = CyberCyan,
                modifier = Modifier.size(44.dp)
            )

            Text(
                text = "Gemma 3n Otonom Agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Telefonda bağımsız çalışan kodlama ve dosya yönetimi agentı. Dosya oluşturabilir, düzenleyebilir, kod çalıştırabilir ve web projelerinizi canlı test edebilir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = "Hemen Deneyebileceğiniz Görevler:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )

            val samples = listOf(
                "demo_project/index.html dosyasını oku ve tasarımını modernleştir",
                "demo_project/calculator.html dosyasında çalışan bir hesap makinesi yap",
                "1 ile 50 arasındaki Fibonacci sayılarını hesaplayan script çalıştır"
            )

            samples.forEach { sample ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrompt(sample) }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Seç",
                            tint = NeonPurple,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = sample,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
