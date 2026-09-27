package com.example.domain.llm

enum class ModelMode {
    GEMMA_LOCAL_3N,
    GEMINI_CLOUD
}

data class ModelConfig(
    val mode: ModelMode = ModelMode.GEMINI_CLOUD,
    val localModelPath: String = "",
    val cloudModelName: String = "gemini-3.5-flash",
    val temperature: Float = 0.4f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.95f
)

data class ModelStatus(
    val mode: ModelMode,
    val isReady: Boolean,
    val modelName: String,
    val details: String,
    val memoryUsageMb: Long = 0,
    val localFilePath: String? = null,
    val localFileSizeMb: Long = 0
)

interface LlmEngine {
    val mode: ModelMode
    suspend fun isAvailable(): Boolean
    suspend fun getStatus(): ModelStatus
    suspend fun generate(
        prompt: String,
        systemInstruction: String? = null,
        stopSequences: List<String> = emptyList()
    ): String
}
