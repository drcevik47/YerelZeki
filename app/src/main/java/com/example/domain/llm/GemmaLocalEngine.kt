package com.example.domain.llm

import android.content.Context
import android.os.Environment
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class GemmaLocalEngine(
    private val context: Context,
    private var customModelPath: String? = null,
    private var backend: HardwareBackend = HardwareBackend.AUTO
) : LlmEngine {

    override val mode: ModelMode = ModelMode.GEMMA_LOCAL_3N

    private val hardwareManager = HardwareAccelerationManager(context)
    private val inferenceMutex = Mutex()
    private var activeLlmInference: LlmInference? = null
    private var loadedModelPath: String? = null

    companion object {
        const val KAGGLE_URL = "https://www.kaggle.com/models/google/gemma-3n/tfLite/gemma-3n-e4b-it-int4"
        const val DEFAULT_MODEL_NAME = "gemma-3n-e4b-it-int4"
        val CANDIDATE_FILENAMES = listOf(
            "gemma-3n-e4b-it-int4.bin",
            "gemma-3n-e4b-it-int4.task",
            "gemma-3n-e4b-it-int4.tflite",
            "gemma-3n-it-int4.bin",
            "model.bin",
            "model.task",
            "model.tflite",
            "gemma-2b-it-cpu-int4.bin",
            "gemma-2b-it-gpu-int4.bin"
        )
    }

    fun setModelPath(path: String) {
        customModelPath = path
        // Invalidate cached engine if path changed
        if (loadedModelPath != path) {
            closeEngine()
        }
    }

    fun setHardwareBackend(newBackend: HardwareBackend) {
        if (backend != newBackend) {
            backend = newBackend
            closeEngine()
        }
    }

    fun getHardwareBackend(): HardwareBackend = backend

    fun getResolvedBackend(): HardwareBackend {
        return if (backend == HardwareBackend.AUTO) {
            hardwareManager.getDeviceInfo().recommendedBackend
        } else {
            backend
        }
    }

    fun findModelFile(): File? {
        // 1. Check custom path specified by user
        customModelPath?.takeIf { it.isNotBlank() }?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.length() > 0) {
                return file
            }
        }

        // 2. Check internal app models directory (/data/data/.../files/models/)
        val appModelsDir = File(context.filesDir, "models")
        if (appModelsDir.exists() && appModelsDir.isDirectory) {
            for (name in CANDIDATE_FILENAMES) {
                val candidate = File(appModelsDir, name)
                if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                    return candidate
                }
            }
            appModelsDir.listFiles()?.firstOrNull {
                it.isFile && it.extension in listOf("bin", "task", "tflite") && it.length() > 0
            }?.let { return it }
        }

        // 3. Check public Downloads folder
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null && downloadsDir.exists()) {
                for (name in CANDIDATE_FILENAMES) {
                    val candidate = File(downloadsDir, name)
                    if (candidate.exists() && candidate.isFile && candidate.length() > 0) {
                        return candidate
                    }
                }
                downloadsDir.listFiles()?.firstOrNull {
                    it.name.contains("gemma", ignoreCase = true) &&
                        it.extension in listOf("bin", "task", "tflite") &&
                        it.length() > 0
                }?.let { return it }
            }
        } catch (_: Exception) {}

        return null
    }

    override suspend fun isAvailable(): Boolean {
        return findModelFile() != null
    }

    override suspend fun getStatus(): ModelStatus = withContext(Dispatchers.IO) {
        val file = findModelFile()
        val activeBackend = getResolvedBackend()
        val hwInfo = hardwareManager.getDeviceInfo()

        val backendLabel = when (activeBackend) {
            HardwareBackend.GPU -> "GPU (${hwInfo.vulkanVersion})"
            HardwareBackend.NPU -> "NPU (NNAPI / Hexagon)"
            HardwareBackend.CPU -> "CPU (ARM NEON)"
            HardwareBackend.AUTO -> "Otomatik -> ${hwInfo.recommendedBackend.displayName}"
        }

        if (file != null) {
            val sizeMb = file.length() / (1024 * 1024)
            ModelStatus(
                mode = ModelMode.GEMMA_LOCAL_3N,
                isReady = true,
                modelName = "Gemma 3n ($DEFAULT_MODEL_NAME)",
                details = "Model Dosyası Hazır: ${file.name} (${sizeMb} MB) • $backendLabel",
                localFilePath = file.absolutePath,
                localFileSizeMb = sizeMb
            )
        } else {
            ModelStatus(
                mode = ModelMode.GEMMA_LOCAL_3N,
                isReady = false,
                modelName = "Gemma 3n ($DEFAULT_MODEL_NAME)",
                details = "Model Dosyası Eksik! Lütfen Kaggle'dan indirip 'Gemma 3n' sekmesinden seçin.",
                localFilePath = null,
                localFileSizeMb = 0
            )
        }
    }

    private fun getOrCreateInference(modelFile: File): LlmInference {
        val currentInstance = activeLlmInference
        if (currentInstance != null && loadedModelPath == modelFile.absolutePath) {
            return currentInstance
        }

        closeEngine()

        val optionsBuilder = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(2048)
            .setTemperature(0.4f)
            .setTopK(40)

        val newInstance = LlmInference.createFromOptions(context, optionsBuilder.build())
        activeLlmInference = newInstance
        loadedModelPath = modelFile.absolutePath
        return newInstance
    }

    private fun closeEngine() {
        try {
            activeLlmInference?.close()
        } catch (_: Exception) {}
        activeLlmInference = null
        loadedModelPath = null
    }

    override suspend fun generate(
        prompt: String,
        systemInstruction: String?,
        stopSequences: List<String>
    ): String = withContext(Dispatchers.IO) {
        val modelFile = findModelFile()
            ?: return@withContext "HATA: Yerel Gemma model dosyası (.bin veya .task) bulunamadı.\n\n" +
                "Gerçek model çıkarımı (on-device inference) yapabilmek için lütfen 'Gemma 3n' sekmesinden Kaggle model arşivini (.tar.gz) veya indirdiğiniz model dosyasını uygulamaya yükleyin.\n\n" +
                "Kaggle Model: $KAGGLE_URL"

        inferenceMutex.withLock {
            try {
                val inference = getOrCreateInference(modelFile)

                // Format prompt with system instructions if present
                val formattedPrompt = if (!systemInstruction.isNullOrBlank()) {
                    "<start_of_turn>user\n$systemInstruction\n\n$prompt<end_of_turn>\n<start_of_turn>model\n"
                } else {
                    "<start_of_turn>user\n$prompt<end_of_turn>\n<start_of_turn>model\n"
                }

                val response = inference.generateResponse(formattedPrompt)

                // Clean turn tags if generated
                response.replace("<end_of_turn>", "").trim()
            } catch (t: Throwable) {
                closeEngine()
                "Yerel Gemma Çıkarım Hatası (MediaPipe GenAI): ${t.localizedMessage ?: t.message ?: t.javaClass.simpleName}\n" +
                    "Model Dosyası: ${modelFile.absolutePath} (${modelFile.length() / (1024 * 1024)} MB)\n\n" +
                    "İpucu: Model dosyasının Kaggle MediaPipe formatı ile tam uyumlu olduğundan emin olun."
            }
        }
    }
}
