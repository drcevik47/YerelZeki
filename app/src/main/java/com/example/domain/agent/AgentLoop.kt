package com.example.domain.agent

import com.example.data.db.AgentDao
import com.example.data.db.AgentMessageEntity
import com.example.data.db.ToolExecutionEntity
import com.example.domain.llm.LlmEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

sealed class AgentStepEvent {
    object Idle : AgentStepEvent()
    data class Thinking(val step: Int, val maxSteps: Int, val message: String) : AgentStepEvent()
    data class ExecutingTool(val step: Int, val toolName: String, val args: Map<String, String>) : AgentStepEvent()
    data class ToolDone(val step: Int, val toolName: String, val output: String, val success: Boolean) : AgentStepEvent()
    data class Finished(val finalAnswer: String) : AgentStepEvent()
    data class Error(val error: String) : AgentStepEvent()
}

class AgentLoop(
    private val agentDao: AgentDao,
    private val toolRegistry: AgentToolRegistry
) {

    private val _currentEvent = MutableStateFlow<AgentStepEvent>(AgentStepEvent.Idle)
    val currentEvent: StateFlow<AgentStepEvent> = _currentEvent.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    fun buildSystemPrompt(): String {
        return """
Sen Android cihazında çalışan uzman bir otonom yapay zeka yazılım geliştirme ve dosya sistemi agentısın (Gemma 3n Agent).
Kullanıcılar senden dosya okuma, yazma, düzenleme, silme, dizin listeleme ve Python, Java, JavaScript, Kotlin gibi dillerde çalışan kod üretimi isteyecektir.

${toolRegistry.getToolDocumentation()}

Yetkilerin ve Yönergelerin:
1. Android Dosya Sistemi:
   - Dosyaları okumak için: `read_file`
   - Yeni dosya oluşturmak veya kaydetmek için: `write_file`
   - Var olan kodları düzenlemek için: `edit_file`
   - Dosya/klasör silmek için: `delete_file`
   - Klasör içeriğini görmek için: `list_directory`
   - Dosya izin ve boyut bilgisi için: `file_info`
2. Kod Üretme Yeteneği:
   - Kullanıcı belirli bir dilde (Python, Java, JavaScript, Kotlin vb.) kod istediğinde, kodu eksiksiz, çalışan, hata yakalama bloklarına sahip ve doğru sözdiziminde üret.
   - Kodları kullanıcı talebine göre dosya sistemine doğrudan kaydet (örn: `write_file` ile `demo_project/script.py` veya `Main.java`).
3. ReAct Çalışma Protokolü:
   - THOUGHT: Ne yapacağını ve adımlarını açıkla.
   - ACTION: Araç çağrısı (JSON formatında).
   - OBSERVATION: Sistemden gelen araç çıktısı.
   - FINAL_ANSWER: Görev tamamlandığında yapılan işlemleri ve üretilen kodları özetle.

Format:
THOUGHT: [Düşünce metni]
ACTION: ```json
{"tool": "araç_adı", "parametre1": "değer1"}
```
""".trimIndent()
    }

    suspend fun runGoal(
        sessionId: String,
        userGoal: String,
        engine: LlmEngine,
        maxSteps: Int = 8
    ) {
        if (_isRunning.value) return
        _isRunning.value = true

        try {
            // Save user message
            agentDao.insertMessage(
                AgentMessageEntity(
                    sessionId = sessionId,
                    sender = "user",
                    content = userGoal
                )
            )

            val conversationHistory = StringBuilder()
            conversationHistory.append("Kullanıcı Hedefi: ").append(userGoal).append("\n\n")

            val systemPrompt = buildSystemPrompt()

            for (step in 1..maxSteps) {
                _currentEvent.value = AgentStepEvent.Thinking(step, maxSteps, "Düşünülüyor...")

                val prompt = conversationHistory.toString()
                val rawModelOutput = try {
                    engine.generate(
                        prompt = prompt,
                        systemInstruction = systemPrompt,
                        stopSequences = listOf("OBSERVATION:", "GÖZLEM:")
                    )
                } catch (e: Exception) {
                    val err = "Model çalıştırma hatası: ${e.message}"
                    _currentEvent.value = AgentStepEvent.Error(err)
                    agentDao.insertMessage(
                        AgentMessageEntity(
                            sessionId = sessionId,
                            sender = "system",
                            content = err,
                            status = "ERROR"
                        )
                    )
                    _isRunning.value = false
                    return
                }

                val parsed = ReActParser.parse(rawModelOutput)

                // If model made a tool call
                if (parsed.toolCall != null) {
                    val toolCall = parsed.toolCall
                    _currentEvent.value = AgentStepEvent.ExecutingTool(step, toolCall.name, toolCall.arguments)

                    // Execute Tool
                    val toolResult = toolRegistry.executeTool(toolCall)

                    _currentEvent.value = AgentStepEvent.ToolDone(
                        step = step,
                        toolName = toolCall.name,
                        output = toolResult.output,
                        success = toolResult.isSuccess
                    )

                    // Persist tool execution
                    agentDao.insertToolExecution(
                        ToolExecutionEntity(
                            sessionId = sessionId,
                            toolName = toolCall.name,
                            inputArgs = JSONObject(toolCall.arguments).toString(),
                            outputResult = toolResult.output,
                            executionTimeMs = toolResult.durationMs,
                            isSuccess = toolResult.isSuccess
                        )
                    )

                    // Save message turn
                    agentDao.insertMessage(
                        AgentMessageEntity(
                            sessionId = sessionId,
                            sender = "agent",
                            content = parsed.thought ?: "Araç çalıştırıldı: ${toolCall.name}",
                            thought = parsed.thought,
                            toolCallName = toolCall.name,
                            toolCallArgs = JSONObject(toolCall.arguments).toString(),
                            toolResult = toolResult.output,
                            status = if (toolResult.isSuccess) "SUCCESS" else "ERROR"
                        )
                    )

                    // Append to conversation context for next step
                    conversationHistory.append(rawModelOutput).append("\n\n")
                    conversationHistory.append("OBSERVATION: ").append(toolResult.output).append("\n\n")
                } else {
                    // Final answer or no more tools
                    val answer = parsed.finalAnswer ?: rawModelOutput
                    _currentEvent.value = AgentStepEvent.Finished(answer)

                    agentDao.insertMessage(
                        AgentMessageEntity(
                            sessionId = sessionId,
                            sender = "agent",
                            content = answer,
                            thought = parsed.thought
                        )
                    )
                    break
                }
            }
        } catch (e: Exception) {
            _currentEvent.value = AgentStepEvent.Error(e.message ?: "Bilinmeyen hata")
        } finally {
            _isRunning.value = false
        }
    }
}
