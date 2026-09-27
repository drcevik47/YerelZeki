package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.AgentViewModel
import com.example.ui.AppTab
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme

data class NavItem(
    val tab: AppTab,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {

    private val viewModel: AgentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppScreen(viewModel: AgentViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()

    // Request permissions for storage on older versions if needed
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.refreshFiles()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val navItems = listOf(
        NavItem(AppTab.AGENT_CHAT, "Agent", Icons.Default.SmartToy, "nav_agent"),
        NavItem(AppTab.WORKSPACE_FILES, "Dosyalar", Icons.Default.Folder, "nav_files"),
        NavItem(AppTab.CODE_EDITOR, "Kodlama", Icons.Default.Code, "nav_editor"),
        NavItem(AppTab.LIVE_PREVIEW, "Önizleme", Icons.Default.PlayCircle, "nav_preview"),
        NavItem(AppTab.SETTINGS, "Gemma 3n", Icons.Default.Settings, "nav_settings")
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                navItems.forEach { item ->
                    val selected = currentTab == item.tab
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.setTab(item.tab) },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        modifier = Modifier.testTag(item.testTag),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val modifier = Modifier.padding(innerPadding)
        when (currentTab) {
            AppTab.AGENT_CHAT -> AgentChatScreen(viewModel = viewModel, modifier = modifier)
            AppTab.WORKSPACE_FILES -> WorkspaceExplorerScreen(viewModel = viewModel, modifier = modifier)
            AppTab.CODE_EDITOR -> CodeEditorScreen(viewModel = viewModel, modifier = modifier)
            AppTab.LIVE_PREVIEW -> LivePreviewScreen(viewModel = viewModel, modifier = modifier)
            AppTab.SETTINGS -> ModelSettingsScreen(viewModel = viewModel, modifier = modifier)
        }
    }
}
