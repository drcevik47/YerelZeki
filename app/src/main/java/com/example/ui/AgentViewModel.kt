package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AgentDatabase
import com.example.data.db.AgentMessageEntity
import com.example.data.db.AgentSessionEntity
import com.example.data.workspace.WorkspaceFile
import com.example.data.workspace.WorkspaceManager
import com.example.domain.agent.AgentLoop
import com.example.domain.agent.AgentStepEvent
import com.example.domain.agent.AgentToolRegistry
import com.example.domain.agent.CodeSandboxRunner
import com.example.domain.llm.ExtractionState
import com.example.domain.llm.HardwareAccelerationManager
import com.example.domain.llm.HardwareBackend
import com.example.domain.llm.ModelArchiveExtractor
import com.example.domain.llm.ModelManager
import com.example.domain.llm.ModelMode
import com.example.domain.llm.ModelStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class AppTab {
    AGENT_CHAT,
    WORKSPACE_FILES,
    CODE_EDITOR,
    LIVE_PREVIEW,
    SETTINGS
}

class AgentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AgentDatabase.getDatabase(application)
    val dao = db.agentDao()

    val workspaceManager = WorkspaceManager(application)
    val codeSandboxRunner = CodeSandboxRunner(application)
    val toolRegistry = AgentToolRegistry(workspaceManager, codeSandboxRunner)
    val modelManager = ModelManager(application)
    val agentLoop = AgentLoop(dao, toolRegistry)

    // Current Tab
    private val _currentTab = MutableStateFlow(AppTab.AGENT_CHAT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Sessions
    val sessions: StateFlow<List<AgentSessionEntity>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeSessionId = MutableStateFlow<String>("")
    val activeSessionId: StateFlow<String> = _activeSessionId.asStateFlow()

    // Messages for active session
    val messages: StateFlow<List<AgentMessageEntity>> = _activeSessionId.flatMapLatest { id ->
        if (id.isNotBlank()) dao.getMessagesForSession(id) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Agent execution status
    val currentStepEvent: StateFlow<AgentStepEvent> = agentLoop.currentEvent
    val isRunning: StateFlow<Boolean> = agentLoop.isRunning

    // Workspace files
    private val _workspaceFiles = MutableStateFlow<List<WorkspaceFile>>(emptyList())
    val workspaceFiles: StateFlow<List<WorkspaceFile>> = _workspaceFiles.asStateFlow()

    // Code Editor
    private val _editorFilePath = MutableStateFlow<String>("")
    val editorFilePath: StateFlow<String> = _editorFilePath.asStateFlow()

    private val _editorContent = MutableStateFlow<String>("")
    val editorContent: StateFlow<String> = _editorContent.asStateFlow()

    private val _editorNotification = MutableStateFlow<String?>(null)
    val editorNotification: StateFlow<String?> = _editorNotification.asStateFlow()

    // Live Preview
    private val _previewHtml = MutableStateFlow<String>("")
    val previewHtml: StateFlow<String> = _previewHtml.asStateFlow()

    // Model Status
    val modelStatus: StateFlow<ModelStatus?> = modelManager.status
    val modelMode: StateFlow<ModelMode> = modelManager.currentMode
    val hardwareBackend: StateFlow<HardwareBackend> = modelManager.hardwareBackend

    val hardwareInfo = HardwareAccelerationManager(application).getDeviceInfo()
    val archiveExtractor = ModelArchiveExtractor(application)

    data class ModelImportProgress(
        val isImporting: Boolean = false,
        val message: String = "",
        val percentage: Float = 0f,
        val isSuccess: Boolean = false,
        val isError: Boolean = false
    )

    private val _modelImportProgress = MutableStateFlow(ModelImportProgress())
    val modelImportProgress: StateFlow<ModelImportProgress> = _modelImportProgress.asStateFlow()

    private var currentExecutionJob: Job? = null

    init {
        viewModelScope.launch {
            workspaceManager.initializeWorkspaceIfNeeded()
            refreshFiles()
            modelManager.refreshStatus()

            // Initialize or restore session
            sessions.collect { list ->
                if (list.isNotEmpty() && _activeSessionId.value.isBlank()) {
                    _activeSessionId.value = list.first().id
                } else if (list.isEmpty() && _activeSessionId.value.isBlank()) {
                    createNewSession("Yeni Görev")
                }
            }
        }
    }

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
        if (tab == AppTab.WORKSPACE_FILES) {
            refreshFiles()
        }
    }

    fun createNewSession(title: String = "Yeni Görev") {
        viewModelScope.launch {
            val newId = UUID.randomUUID().toString()
            val session = AgentSessionEntity(
                id = newId,
                title = title
            )
            dao.insertSession(session)
            _activeSessionId.value = newId
        }
    }

    fun switchSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            dao.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = ""
            }
        }
    }

    fun runGoal(goal: String) {
        if (goal.isBlank() || isRunning.value) return
        val sessionId = _activeSessionId.value
        if (sessionId.isBlank()) return

        currentExecutionJob = viewModelScope.launch {
            val engine = modelManager.getActiveEngine()
            agentLoop.runGoal(sessionId, goal, engine)
            refreshFiles()
        }
    }

    fun cancelExecution() {
        currentExecutionJob?.cancel()
        currentExecutionJob = null
    }

    fun refreshFiles(path: String = "") {
        viewModelScope.launch {
            _workspaceFiles.value = workspaceManager.listFiles(path, recursive = true)
        }
    }

    fun openFileInEditor(filePath: String) {
        viewModelScope.launch {
            try {
                val content = workspaceManager.readFile(filePath)
                _editorFilePath.value = filePath
                _editorContent.value = content
                _currentTab.value = AppTab.CODE_EDITOR
            } catch (e: Exception) {
                _editorNotification.value = "Dosya açılamadı: ${e.message}"
            }
        }
    }

    fun updateEditorContent(newContent: String) {
        _editorContent.value = newContent
    }

    fun saveEditorFile() {
        viewModelScope.launch {
            val path = _editorFilePath.value
            if (path.isNotBlank()) {
                try {
                    workspaceManager.writeFile(path, _editorContent.value, overwrite = true)
                    _editorNotification.value = "Dosya kaydedildi ($path)"
                    refreshFiles()
                } catch (e: Exception) {
                    _editorNotification.value = "Kaydetme hatası: ${e.message}"
                }
            }
        }
    }

    fun previewHtmlFile(filePath: String) {
        viewModelScope.launch {
            try {
                val content = workspaceManager.readFile(filePath)
                _previewHtml.value = content
                _currentTab.value = AppTab.LIVE_PREVIEW
            } catch (e: Exception) {
                _editorNotification.value = "Önizleme yüklenemedi: ${e.message}"
            }
        }
    }

    fun clearNotification() {
        _editorNotification.value = null
    }

    fun createNewFileInWorkspace(fileName: String, initialContent: String = "") {
        viewModelScope.launch {
            try {
                val clean = if (fileName.contains("/")) fileName else "demo_project/$fileName"
                workspaceManager.writeFile(clean, initialContent, overwrite = false)
                refreshFiles()
                openFileInEditor(clean)
            } catch (e: Exception) {
                _editorNotification.value = "Oluşturulamadı: ${e.message}"
            }
        }
    }

    fun deleteWorkspaceFile(filePath: String) {
        viewModelScope.launch {
            try {
                workspaceManager.deleteFile(filePath)
                refreshFiles()
                if (_editorFilePath.value == filePath) {
                    _editorFilePath.value = ""
                    _editorContent.value = ""
                }
            } catch (e: Exception) {
                _editorNotification.value = "Silme hatası: ${e.message}"
            }
        }
    }

    fun generateCodeForLanguage(language: String, purpose: String, fileName: String? = null) {
        val targetFile = fileName ?: when (language.lowercase()) {
            "python", "py" -> "demo_project/script.py"
            "java" -> "demo_project/Main.java"
            "javascript", "js" -> "demo_project/app.js"
            "kotlin", "kt" -> "demo_project/Main.kt"
            "html" -> "demo_project/index.html"
            else -> "demo_project/code.txt"
        }
        val prompt = "$language programlama dilinde '$purpose' işlevini gerçekleştiren eksiksiz, çalışan ve geçerli bir kod yaz. Kodu '$targetFile' dosyasına kaydet ve sonucunu açıkla."
        _currentTab.value = AppTab.AGENT_CHAT
        runGoal(prompt)
    }

    fun importModelArchiveFromUri(uri: Uri, filename: String) {
        viewModelScope.launch {
            _modelImportProgress.value = ModelImportProgress(
                isImporting = true,
                message = "$filename arşivi ayıklanıyor...",
                percentage = 0.1f
            )

            archiveExtractor.extractArchiveStream(uri, filename).collect { state ->
                when (state) {
                    is ExtractionState.Progress -> {
                        _modelImportProgress.value = ModelImportProgress(
                            isImporting = true,
                            message = "${state.currentFile} (${state.bytesExtracted / (1024 * 1024)} MB)",
                            percentage = state.percentage
                        )
                    }
                    is ExtractionState.Success -> {
                        val modelFile = state.extractedModelFile
                        modelManager.setCustomModelPath(modelFile.absolutePath)
                        modelManager.setMode(ModelMode.GEMMA_LOCAL_3N)
                        modelManager.refreshStatus()
                        _modelImportProgress.value = ModelImportProgress(
                            isImporting = false,
                            message = "Model başarıyla entegre edildi: ${modelFile.name} (${state.totalBytes / (1024 * 1024)} MB)",
                            percentage = 1f,
                            isSuccess = true
                        )
                    }
                    is ExtractionState.Error -> {
                        _modelImportProgress.value = ModelImportProgress(
                            isImporting = false,
                            message = state.message,
                            percentage = 0f,
                            isError = true
                        )
                    }
                }
            }
        }
    }

    fun scanAndExtractDownloadArchive() {
        viewModelScope.launch {
            try {
                val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (downloads != null && downloads.exists()) {
                    val archive = downloads.listFiles()?.firstOrNull {
                        it.name.contains("gemma", ignoreCase = true) &&
                        (it.name.endsWith(".tar.gz") || it.name.endsWith(".tgz") || it.name.endsWith(".zip"))
                    } ?: downloads.listFiles()?.firstOrNull { it.name.endsWith(".tar.gz") }

                    if (archive != null && archive.exists()) {
                        importModelArchiveFromUri(Uri.fromFile(archive), archive.name)
                    } else {
                        _modelImportProgress.value = ModelImportProgress(
                            isImporting = false,
                            message = "Download klasöründe gemma-3n-*.tar.gz bulunamadı.",
                            isError = true
                        )
                    }
                }
            } catch (e: Exception) {
                _modelImportProgress.value = ModelImportProgress(
                    isImporting = false,
                    message = "Hata: ${e.message}",
                    isError = true
                )
            }
        }
    }

    fun dismissImportStatus() {
        _modelImportProgress.value = ModelImportProgress()
    }

    fun setHardwareBackend(backend: HardwareBackend) {
        modelManager.setHardwareBackend(backend)
        viewModelScope.launch {
            modelManager.refreshStatus()
        }
    }

    fun setModelMode(mode: ModelMode) {
        modelManager.setMode(mode)
        viewModelScope.launch {
            modelManager.refreshStatus()
        }
    }

    fun setApiKey(key: String) {
        modelManager.setApiKey(key)
        viewModelScope.launch {
            modelManager.refreshStatus()
        }
    }

    fun setCustomModelPath(path: String) {
        modelManager.setCustomModelPath(path)
        viewModelScope.launch {
            modelManager.refreshStatus()
        }
    }

    fun refreshModelStatus() {
        viewModelScope.launch {
            modelManager.refreshStatus()
        }
    }
}
