package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AgentViewModel
import com.example.ui.AppTab
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val filePath by viewModel.editorFilePath.collectAsState()
    val content by viewModel.editorContent.collectAsState()
    val notification by viewModel.editorNotification.collectAsState()

    var showPromptDialog by remember { mutableStateOf(false) }
    var agentInstruction by remember { mutableStateOf("") }

    var showCodeGenDialog by remember { mutableStateOf(false) }
    var selectedLang by remember { mutableStateOf("Python") }
    var codePurpose by remember { mutableStateOf("") }

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    val lineCount = remember(content) {
        content.split("\n").size.coerceAtLeast(1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (filePath.isNotBlank()) filePath.substringAfterLast('/') else "Kod Düzenleyici & Üretici",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (filePath.isNotBlank()) filePath else "Yeni kod üretmek için 'Kod Üret'e basın",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { showCodeGenDialog = true },
                        modifier = Modifier.padding(end = 4.dp).testTag("btn_open_code_generator")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                        Spacer(Modifier.width(4.dp))
                        Text("Kod Üret", fontSize = 12.sp)
                    }

                    if (filePath.endsWith(".html") || filePath.endsWith(".htm")) {
                        IconButton(
                            onClick = { viewModel.previewHtmlFile(filePath) },
                            modifier = Modifier.testTag("editor_preview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Önizle",
                                tint = EmeraldSuccess
                            )
                        }
                    }

                    IconButton(
                        onClick = { showPromptDialog = true },
                        enabled = filePath.isNotBlank(),
                        modifier = Modifier.testTag("editor_ask_agent_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = "Agent ile Düzenle",
                            tint = NeonPurple
                        )
                    }

                    IconButton(
                        onClick = { viewModel.saveEditorFile() },
                        enabled = filePath.isNotBlank(),
                        modifier = Modifier.testTag("editor_save_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Kaydet",
                            tint = CyberCyan
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkCodeBg)
        ) {
            // Notification pill if saved
            if (notification != null) {
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = notification ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (filePath.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Gemma 3n Çok Dilli Kod Üretici",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Text(
                            text = "Python, Java, JavaScript, Kotlin ve diğer dillerde çalışan geçerli kod üretin veya var olan dosyaları düzenleyin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondaryDark,
                            modifier = Modifier.padding(horizontal = 32.dp),
                            lineHeight = 18.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showCodeGenDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = DarkBackground)
                            ) {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Kod Oluştur")
                            }

                            OutlinedButton(onClick = { viewModel.setTab(AppTab.WORKSPACE_FILES) }) {
                                Text("Dosyaları Aç")
                            }
                        }
                    }
                }
            } else {
                // Code Editor with Line Numbers
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScrollState)
                ) {
                    // Line numbers column
                    Column(
                        modifier = Modifier
                            .background(Color(0xFF0F172A))
                            .padding(vertical = 12.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        for (i in 1..lineCount) {
                            Text(
                                text = "$i",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Code editing content
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState)
                            .padding(vertical = 12.dp, horizontal = 12.dp)
                    ) {
                        BasicTextField(
                            value = content,
                            onValueChange = { viewModel.updateEditorContent(it) },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Color(0xFFF1F5F9),
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(CyberCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 400.dp)
                                .testTag("code_editor_text_field")
                        )
                    }
                }
            }
        }
    }

    // Dialog: Code Generator Dialog
    if (showCodeGenDialog) {
        AlertDialog(
            onDismissRequest = { showCodeGenDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberCyan)
                    Text("Yapay Zeka Kod Üretici")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Programlama Dili Seçin:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    val languages = listOf("Python", "Java", "JavaScript", "Kotlin", "HTML", "SQL")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        languages.forEach { lang ->
                            FilterChip(
                                selected = selectedLang == lang,
                                onClick = { selectedLang = lang },
                                label = { Text(lang, fontSize = 12.sp) }
                            )
                        }
                    }

                    Text("İşlev ve İstenen Fonksiyon:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = codePurpose,
                        onValueChange = { codePurpose = it },
                        placeholder = { Text("Örn: JSON verisini işleyip sıralayan ve filtreleyen fonksiyon yaz") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val purpose = codePurpose.trim()
                        if (purpose.isNotBlank()) {
                            viewModel.generateCodeForLanguage(
                                language = selectedLang,
                                purpose = purpose,
                                fileName = if (filePath.isNotBlank()) filePath else null
                            )
                            codePurpose = ""
                            showCodeGenDialog = false
                        }
                    },
                    enabled = codePurpose.isNotBlank()
                ) {
                    Text("Kodu Üret ve Çalıştır")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCodeGenDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    // Dialog: Ask Agent to edit this file
    if (showPromptDialog) {
        AlertDialog(
            onDismissRequest = { showPromptDialog = false },
            title = {
                Text(
                    text = "Gemma Agent ile Düzenle",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Agent'a '$filePath' dosyası için ne yapmasını istediğinizi söyleyin:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = agentInstruction,
                        onValueChange = { agentInstruction = it },
                        placeholder = { Text("Örn: Yeni fonksiyonlar ekle, hata yakalama ekle...") },
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prompt = "$filePath dosyasını oku ve şu isteğe göre düzenle: ${agentInstruction.trim()}"
                        viewModel.runGoal(prompt)
                        viewModel.setTab(AppTab.AGENT_CHAT)
                        agentInstruction = ""
                        showPromptDialog = false
                    },
                    enabled = agentInstruction.isNotBlank()
                ) {
                    Text("Agent'a Gönder")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromptDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }
}
