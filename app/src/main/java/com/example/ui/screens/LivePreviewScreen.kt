package com.example.ui.screens

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.AgentViewModel
import com.example.ui.AppTab
import com.example.ui.theme.*

data class WebConsoleLog(
    val message: String,
    val level: ConsoleMessage.MessageLevel,
    val lineNumber: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivePreviewScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val htmlContent by viewModel.previewHtml.collectAsState()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    val consoleLogs = remember { mutableStateListOf<WebConsoleLog>() }
    var isConsoleOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Canlı Web Önizleme",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (htmlContent.isNotBlank()) "Yerel Sandbox • HTML5" else "İçerik yüklenmedi",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isConsoleOpen = !isConsoleOpen },
                        modifier = Modifier.testTag("toggle_console_button")
                    ) {
                        BadgedBox(badge = {
                            if (consoleLogs.isNotEmpty()) {
                                Badge { Text("${consoleLogs.size}") }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Konsol",
                                tint = if (isConsoleOpen) CyberCyan else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            consoleLogs.clear()
                            webViewRef?.loadDataWithBaseURL("https://local.preview/", htmlContent, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.testTag("reload_preview_button")
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yeniden Yükle")
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
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (htmlContent.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Web,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(54.dp)
                        )
                        Text(
                            text = "Önizlenecek bir HTML dosyası açılmadı",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                viewModel.previewHtmlFile("demo_project/index.html")
                            }
                        ) {
                            Text("Örnek index.html'i Yükle")
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowFileAccess = true

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        consoleMessage?.let {
                                            consoleLogs.add(
                                                WebConsoleLog(
                                                    message = it.message(),
                                                    level = it.messageLevel(),
                                                    lineNumber = it.lineNumber()
                                                )
                                            )
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }

                                webViewClient = WebViewClient()
                                webViewRef = this
                                loadDataWithBaseURL("https://local.preview/", htmlContent, "text/html", "UTF-8", null)
                            }
                        },
                        update = { webView ->
                            webViewRef = webView
                            webView.loadDataWithBaseURL("https://local.preview/", htmlContent, "text/html", "UTF-8", null)
                        },
                        modifier = Modifier.fillMaxSize().testTag("live_webview")
                    )
                }

                // Interactive Bottom Console Drawer
                AnimatedVisibility(visible = isConsoleOpen) {
                    Surface(
                        color = DarkCodeBg,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "JavaScript Konsolu (${consoleLogs.size})",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { consoleLogs.clear() }) {
                                    Text("Temizle", fontSize = 11.sp, color = TextSecondaryDark)
                                }
                            }

                            HorizontalDivider(color = DarkSurfaceVariant)

                            if (consoleLogs.isEmpty()) {
                                Text(
                                    text = "Henüz console çıktısı yok.",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(consoleLogs) { log ->
                                        val color = when (log.level) {
                                            ConsoleMessage.MessageLevel.ERROR -> RoseError
                                            ConsoleMessage.MessageLevel.WARNING -> AmberWarning
                                            else -> EmeraldSuccess
                                        }
                                        Text(
                                            text = "[${log.level}] L${log.lineNumber}: ${log.message}",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
