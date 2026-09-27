package com.example.domain.llm

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiRemoteEngine(
    private val modelName: String = "gemini-3.5-flash",
    private var apiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY }
) : LlmEngine {

    override val mode: ModelMode = ModelMode.GEMINI_CLOUD

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    override suspend fun isAvailable(): Boolean {
        val key = apiKeyProvider()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    override suspend fun getStatus(): ModelStatus {
        val ready = isAvailable()
        return ModelStatus(
            mode = ModelMode.GEMINI_CLOUD,
            isReady = ready,
            modelName = modelName,
            details = if (ready) "Bulut API Hazır ($modelName)" else "API Anahtarı gereklidir"
        )
    }

    override suspend fun generate(
        prompt: String,
        systemInstruction: String?,
        stopSequences: List<String>
    ): String = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            throw IllegalStateException(
                "Gemini API Anahtarı bulunamadı. Lütfen Ayarlar panelinden bir anahtar girin veya yerel Gemma 3n model dosyasını yükleyin."
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"

        val rootJson = JSONObject()

        // Contents
        val contentsArray = JSONArray()
        val userContent = JSONObject()
        userContent.put("role", "user")
        val partsArray = JSONArray()
        partsArray.put(JSONObject().put("text", prompt))
        userContent.put("parts", partsArray)
        contentsArray.put(userContent)
        rootJson.put("contents", contentsArray)

        // System Instruction
        if (!systemInstruction.isNullOrBlank()) {
            val sysContent = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", systemInstruction))
            sysContent.put("parts", sysParts)
            rootJson.put("systemInstruction", sysContent)
        }

        // Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", 0.3)
        genConfig.put("maxOutputTokens", 2048)
        if (stopSequences.isNotEmpty()) {
            val stopArray = JSONArray()
            stopSequences.forEach { stopArray.put(it) }
            genConfig.put("stopSequences", stopArray)
        }
        rootJson.put("generationConfig", genConfig)

        val body = rootJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            val errorMsg = try {
                val errObj = JSONObject(responseBody).optJSONObject("error")
                errObj?.optString("message") ?: "HTTP ${response.code}: $responseBody"
            } catch (e: Exception) {
                "HTTP ${response.code}: $responseBody"
            }
            throw RuntimeException("Gemini API Hatası: $errorMsg")
        }

        val json = JSONObject(responseBody)
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return@withContext "Model boş bir yanıt döndürdü."
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            return@withContext "Yanıt içeriği bulunamadı."
        }

        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            sb.append(part.optString("text"))
        }

        sb.toString()
    }
}
