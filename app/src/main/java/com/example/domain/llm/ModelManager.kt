package com.example.domain.llm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("gemma_agent_model_prefs", Context.MODE_PRIVATE)

    val localEngine = GemmaLocalEngine(
        context,
        prefs.getString("custom_model_path", null),
        try {
            HardwareBackend.valueOf(prefs.getString("hardware_backend", HardwareBackend.AUTO.name) ?: HardwareBackend.AUTO.name)
        } catch (_: Exception) {
            HardwareBackend.AUTO
        }
    )

    private val _hardwareBackend = MutableStateFlow(
        try {
            HardwareBackend.valueOf(prefs.getString("hardware_backend", HardwareBackend.AUTO.name) ?: HardwareBackend.AUTO.name)
        } catch (_: Exception) {
            HardwareBackend.AUTO
        }
    )
    val hardwareBackend: StateFlow<HardwareBackend> = _hardwareBackend.asStateFlow()

    fun setHardwareBackend(backend: HardwareBackend) {
        _hardwareBackend.value = backend
        prefs.edit().putString("hardware_backend", backend.name).apply()
        localEngine.setHardwareBackend(backend)
    }

    val remoteEngine = GeminiRemoteEngine(
        modelName = prefs.getString("remote_model_name", "gemini-3.5-flash") ?: "gemini-3.5-flash",
        apiKeyProvider = { getApiKey() }
    )

    private val _currentMode = MutableStateFlow(
        try {
            ModelMode.valueOf(prefs.getString("model_mode", ModelMode.GEMINI_CLOUD.name) ?: ModelMode.GEMINI_CLOUD.name)
        } catch (_: Exception) {
            ModelMode.GEMINI_CLOUD
        }
    )
    val currentMode: StateFlow<ModelMode> = _currentMode.asStateFlow()

    private val _status = MutableStateFlow<ModelStatus?>(null)
    val status: StateFlow<ModelStatus?> = _status.asStateFlow()

    fun getApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun setApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key).apply()
    }

    fun setCustomModelPath(path: String) {
        prefs.edit().putString("custom_model_path", path).apply()
        localEngine.setModelPath(path)
    }

    fun getCustomModelPath(): String {
        return prefs.getString("custom_model_path", "") ?: ""
    }

    fun setMode(mode: ModelMode) {
        _currentMode.value = mode
        prefs.edit().putString("model_mode", mode.name).apply()
    }

    suspend fun refreshStatus(): ModelStatus {
        val st = when (_currentMode.value) {
            ModelMode.GEMMA_LOCAL_3N -> localEngine.getStatus()
            ModelMode.GEMINI_CLOUD -> remoteEngine.getStatus()
        }
        _status.value = st
        return st
    }

    suspend fun getActiveEngine(): LlmEngine {
        val mode = _currentMode.value
        return when (mode) {
            ModelMode.GEMMA_LOCAL_3N -> {
                if (localEngine.isAvailable()) {
                    localEngine
                } else if (remoteEngine.isAvailable()) {
                    // Smart fallback with notification
                    remoteEngine
                } else {
                    localEngine
                }
            }
            ModelMode.GEMINI_CLOUD -> remoteEngine
        }
    }
}
