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

    private val _currentMode = MutableStateFlow(ModelMode.GEMMA_LOCAL_3N)
    val currentMode: StateFlow<ModelMode> = _currentMode.asStateFlow()

    private val _status = MutableStateFlow<ModelStatus?>(null)
    val status: StateFlow<ModelStatus?> = _status.asStateFlow()

    fun setCustomModelPath(path: String) {
        prefs.edit().putString("custom_model_path", path).apply()
        localEngine.setModelPath(path)
    }

    fun getCustomModelPath(): String {
        return prefs.getString("custom_model_path", "") ?: ""
    }

    fun setMode(mode: ModelMode) {
        _currentMode.value = ModelMode.GEMMA_LOCAL_3N
    }

    suspend fun refreshStatus(): ModelStatus {
        val st = localEngine.getStatus()
        _status.value = st
        return st
    }

    suspend fun getActiveEngine(): LlmEngine {
        // App runs strictly on local AI
        return localEngine
    }
}
