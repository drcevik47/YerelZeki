package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.example.data.workspace.WorkspaceFile
import com.example.ui.AgentViewModel
import com.example.ui.AppTab
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceExplorerScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val files by viewModel.workspaceFiles.collectAsState()
    val notification by viewModel.editorNotification.collectAsState()

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }
    var fileToDelete by remember { mutableStateOf<WorkspaceFile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshFiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dosya Gezgini (Workspace)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${files.size} dosya/klasör • /workspace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refreshFiles() },
                        modifier = Modifier.testTag("refresh_files_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yenile")
                    }
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier.testTag("create_file_button")
                    ) {
                        Icon(imageVector = Icons.Default.NoteAdd, contentDescription = "Yeni Dosya", tint = CyberCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewFileDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Yeni Dosya") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_new_file")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Notification snack if any
            if (notification != null) {
                Surface(
                    color = EmeraldSuccess.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = notification ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = EmeraldSuccess
                        )
                        IconButton(
                            onClick = { viewModel.clearNotification() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Kapat", tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Çalışma alanında dosya bulunamadı",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { viewModel.createNewFileInWorkspace("demo_project/index.html") }) {
                            Text("Örnek Dosya Oluştur")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(files, key = { it.relativePath }) { file ->
                        WorkspaceFileRow(
                            file = file,
                            onOpenEditor = { viewModel.openFileInEditor(file.relativePath) },
                            onPreview = { viewModel.previewHtmlFile(file.relativePath) },
                            onDelete = { fileToDelete = file },
                            onAskAgent = {
                                viewModel.runGoal("${file.relativePath} dosyasını incele ve bana özetle.")
                                viewModel.setTab(AppTab.AGENT_CHAT)
                            }
                        )
                    }
                }
            }
        }
    }

    // New File Dialog
    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            title = { Text("Yeni Dosya Oluştur") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Dosya adı ve uzantısını girin (örn: game.js, todo.html, notes.txt):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("demo_project/script.js") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFileName.trim()
                        if (name.isNotBlank()) {
                            viewModel.createNewFileInWorkspace(name)
                            newFileName = ""
                            showNewFileDialog = false
                        }
                    },
                    enabled = newFileName.isNotBlank()
                ) {
                    Text("Oluştur")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewFileDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (fileToDelete != null) {
        val file = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Dosyayı Sil") },
            text = {
                Text("${file.name} dosyasını çalışma alanından kalıcı olarak silmek istediğinizden emin misiniz?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWorkspaceFile(file.relativePath)
                        fileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseError)
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
fun WorkspaceFileRow(
    file: WorkspaceFile,
    onOpenEditor: () -> Unit,
    onPreview: () -> Unit,
    onDelete: () -> Unit,
    onAskAgent: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !file.isDirectory) { onOpenEditor() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // File Type Icon
                val icon = when {
                    file.isDirectory -> Icons.Default.Folder
                    file.extension in listOf("html", "htm") -> Icons.Default.Language
                    file.extension in listOf("js", "ts") -> Icons.Default.Javascript
                    file.extension in listOf("kt", "java") -> Icons.Default.Code
                    file.extension in listOf("css") -> Icons.Default.Brush
                    file.extension in listOf("json") -> Icons.Default.DataObject
                    else -> Icons.Default.Description
                }

                val iconColor = when {
                    file.isDirectory -> AmberWarning
                    file.extension in listOf("html", "htm") -> CyberCyan
                    file.extension in listOf("js", "ts") -> NeonPurple
                    file.extension in listOf("kt", "java") -> EmeraldSuccess
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(26.dp)
                )

                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (file.isDirectory) "Klasör" else "${file.relativePath} • ${file.sizeBytes} B",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Icons
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (file.extension in listOf("html", "htm")) {
                    IconButton(onClick = onPreview, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Önizle",
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (!file.isDirectory) {
                    IconButton(onClick = onAskAgent, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = "Agent'a Sor",
                            tint = NeonPurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Sil",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
