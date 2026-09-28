package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.llm.GemmaLocalEngine
import com.example.domain.llm.HardwareBackend
import com.example.ui.AgentViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsScreen(
    viewModel: AgentViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val modelStatus by viewModel.modelStatus.collectAsState()
    val currentHardwareBackend by viewModel.hardwareBackend.collectAsState()
    val importProgress by viewModel.modelImportProgress.collectAsState()
    val hwInfo = viewModel.hardwareInfo

    var customPathInput by remember { mutableStateOf(viewModel.modelManager.getCustomModelPath()) }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME) ?: -1
            cursor?.moveToFirst()
            val displayName = if (nameIndex >= 0) cursor?.getString(nameIndex) ?: "gemma-3n-model.tar.gz" else "gemma-3n-model.tar.gz"
            cursor?.close()
            viewModel.importModelArchiveFromUri(uri, displayName)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModelStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Yerel Model & GPU/NPU Hızlandırma",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshModelStatus() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Yenile")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Local Only Privacy & Security Notice Badge
            Surface(
                color = EmeraldSuccess.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "%100 Yerel ve Çevrimdışı (On-Device)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                        Text(
                            text = "Uygulama yalnızca cihazınızdaki yerel yapay zeka modelini kullanır. Hiçbir veri veya kod bulut sunucularına gönderilmez.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Incompatible .litertlm format warning card
            val activeFilePath = modelStatus?.localFilePath ?: ""
            val isLitertlmFormat = activeFilePath.endsWith(".litertlm", ignoreCase = true)

            if (isLitertlmFormat) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = RoseError.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, RoseError),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = RoseError,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "Format Uyuşmazlığı: .litertlm Dosyası",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoseError
                            )
                        }

                        Text(
                            text = "Yüklü model: '${activeFilePath.substringAfterLast('/')}' (${modelStatus?.localFileSizeMb ?: 0} MB)\n\n" +
                                "Bu dosya Kaggle üzerindeki yeni 'LiteRT-LM' formatındadır. Ancak Android MediaPipe motoru Kaggle'daki 'TFLite' formatını (.bin veya .task) gerektirir. Dosya yapısı farklı olduğundan 'modelError building tflite model' hatası alırsınız.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )

                        Text(
                            text = "✅ Çözüm: Kaggle'da 'Variation / Framework' kısmından 'TFLite' seçeneğini seçip indirin.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GemmaLocalEngine.KAGGLE_URL))
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = Color.Black)
                            ) {
                                Text("TFLite Modelini Aç", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.deleteInstalledModel()
                                    customPathInput = ""
                                    Toast.makeText(context, "Uyumsuz model silindi", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError)
                            ) {
                                Text("Modeli Sil (Hafıza Boşalt)", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Gemma 3n Kaggle Model Banner Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(if (modelStatus?.isReady == true) EmeraldSuccess else AmberWarning)
                        )
                        Text(
                            text = "Gemma 3n On-Device LLM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    }

                    Text(
                        text = "Google Gemma 3n int4 (e4b-it-int4) modeli telefonda doğrudan GPU ve NPU hızlandırma destekli çalıştırılmak üzere entegre edilmiştir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Surface(
                        color = DarkCodeBg,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Kaggle Model Bağlantısı:",
                                fontSize = 11.sp,
                                color = TextSecondaryDark,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = GemmaLocalEngine.KAGGLE_URL,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberCyan
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Kaggle URL", GemmaLocalEngine.KAGGLE_URL)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Kaggle linki kopyalandı", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Linki Kopyala", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GemmaLocalEngine.KAGGLE_URL))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Gemma 3n TFLite", fontSize = 11.sp)
                        }
                    }

                    // Direct Gemma 2B TFLite alternative link
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.kaggle.com/models/google/gemma/tfLite/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyberCyan)
                        Spacer(Modifier.width(6.dp))
                        Text("Alternatif: Hafif Gemma 2B TFLite (.bin) İndir (1.3 GB)", fontSize = 11.sp)
                    }

                    if (modelStatus?.localFilePath?.isNotBlank() == true) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteInstalledModel()
                                customPathInput = ""
                                Toast.makeText(context, "Model dosyası silindi", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mevcut Modeli Cihazdan Sil (${modelStatus?.localFileSizeMb ?: 0} MB)", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Model Archive (.tar.gz / .zip) Extractor & Loader Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "İndirilmiş Modeli (.tar.gz) Uygulamaya Entegre Et",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                    }

                    Text(
                        text = "Kaggle'dan inen 'gemma-3n-tflite-gemma-3n-e4b-it-int4-v1.tar.gz' arşivini veya ayıklanmış .bin/.task dosyasını doğrudan uygulamaya aktarın. Uygulama arşivi otomatik olarak telefonda açar ve GPU/NPU motoruna bağlar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    // Extraction status progress
                    AnimatedVisibility(visible = importProgress.isImporting || importProgress.isSuccess || importProgress.isError) {
                        Surface(
                            color = when {
                                importProgress.isSuccess -> EmeraldSuccess.copy(alpha = 0.15f)
                                importProgress.isError -> RoseError.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = importProgress.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            importProgress.isSuccess -> EmeraldSuccess
                                            importProgress.isError -> RoseError
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                    if (importProgress.isSuccess || importProgress.isError) {
                                        IconButton(
                                            onClick = { viewModel.dismissImportStatus() },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Kapat", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                if (importProgress.isImporting) {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = EmeraldSuccess
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                modelPickerLauncher.launch(arrayOf("*/*"))
                            },
                            modifier = Modifier.weight(1f).testTag("btn_select_model_archive"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess, contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Dosyayı Seç (.tar.gz)", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.scanAndExtractDownloadArchive()
                            },
                            modifier = Modifier.weight(1f).testTag("btn_auto_scan_download")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download'da Ara", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Hardware Acceleration (GPU / NPU) Settings
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Memory,
                            contentDescription = null,
                            tint = NeonPurple,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "GPU / NPU Donanım Hızlandırma",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = NeonPurple
                        )
                    }

                    Text(
                        text = "Gemma 3n modelinin mobil çip üzerinde en yüksek hızda ve minimum pil tüketimiyle çalışması için hızlandırıcı birimini seçin:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Hardware backend chips
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HardwareBackend.values().forEach { backend ->
                            val isSelected = currentHardwareBackend == backend
                            Surface(
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) CyberCyan else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.setHardwareBackend(backend) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { viewModel.setHardwareBackend(backend) }
                                    )
                                    Column {
                                        Text(
                                            text = backend.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = backend.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Device Hardware Diagnostics Info Box
                    Surface(
                        color = DarkCodeBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "📱 Cihaz Donanım Raporu:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                            Text(
                                text = "• Cihaz / Çip: ${hwInfo.hardwareName} (${hwInfo.socModel})",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "• GPU Desteği: ${if (hwInfo.supportsVulkan) "Vulkan Aktif (${hwInfo.vulkanVersion})" else "OpenGLES Standart"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (hwInfo.supportsVulkan) EmeraldSuccess else AmberWarning
                            )
                            Text(
                                text = "• NPU (AI Çipi): ${if (hwInfo.npuSupported) "Destekleniyor (NNAPI/Hexagon/APU)" else "Standart NNAPI"}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = if (hwInfo.npuSupported) EmeraldSuccess else TextSecondaryDark
                            )
                            Text(
                                text = "• Bellek (RAM): ${hwInfo.availableRamMb} MB boş / ${hwInfo.totalRamMb} MB toplam",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimaryDark
                            )
                            Text(
                                text = "• Önerilen Hızlandırıcı: ${hwInfo.recommendedBackend.displayName}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = CyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Local File Path Configuration
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Yerel Model Dosyası Yolu",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Telefonunuza indirdiğiniz .bin / .task dosyasının tam yolunu manuel olarak da girebilirsiniz:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        placeholder = { Text("/sdcard/Download/gemma-3n-e4b-it-int4.bin") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            viewModel.setCustomModelPath(customPathInput.trim())
                            Toast.makeText(context, "Model yolu kaydedildi", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Yolu Kaydet")
                    }
                }
            }
        }
    }
}
