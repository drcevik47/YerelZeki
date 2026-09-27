package com.example.domain.llm

enum class ModelMode {
    GEMMA_LOCAL_3N
}

data class ModelConfig(
    val mode: ModelMode = ModelMode.GEMMA_LOCAL_3N,
    val localModelPath: String = "",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 2048,
    val topP: Float = 0.95f
)

data class ModelStatus(
    val mode: ModelMode = ModelMode.GEMMA_LOCAL_3N,
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
