package com.example.domain.agent

import org.json.JSONObject

data class ParsedTurn(
    val thought: String?,
    val toolCall: ToolCall?,
    val finalAnswer: String?
)

object ReActParser {

    fun parse(rawResponse: String): ParsedTurn {
        val text = rawResponse.trim()

        // 1. Try to extract THOUGHT
        var thought: String? = null
        val thoughtRegex = Regex("""(?i)(?:THOUGHT|DÜŞÜNCE):\s*(.*?)(?=(?:ACTION|ARAÇ|FINAL_ANSWER|SONUÇ|```|$))""", RegexOption.DOT_MATCHES_ALL)
        val thoughtMatch = thoughtRegex.find(text)
        if (thoughtMatch != null) {
            thought = thoughtMatch.groupValues[1].trim()
        }

        // 2. Try JSON Block Tool Call e.g. ```json { "tool": "..." } ```
        val jsonBlockRegex = Regex("""```(?:json)?\s*(\{[\s\S]*?"tool"[\s\S]*?\})\s*```""", RegexOption.IGNORE_CASE)
        val jsonMatch = jsonBlockRegex.find(text)
        if (jsonMatch != null) {
            val jsonStr = jsonMatch.groupValues[1]
            val toolCall = parseJsonToolCall(jsonStr)
            if (toolCall != null) {
                return ParsedTurn(thought = thought, toolCall = toolCall, finalAnswer = null)
            }
        }

        // 3. Try inline JSON { "tool": "write_file", ... }
        val rawJsonRegex = Regex("""(\{[\s\r\n]*"tool"[\s\S]*?\})""", RegexOption.IGNORE_CASE)
        val rawJsonMatch = rawJsonRegex.find(text)
        if (rawJsonMatch != null) {
            val toolCall = parseJsonToolCall(rawJsonMatch.groupValues[1])
            if (toolCall != null) {
                return ParsedTurn(thought = thought, toolCall = toolCall, finalAnswer = null)
            }
        }

        // 4. Try ACTION: tool_name { ... } or ACTION: tool_name( ... )
        val actionRegex = Regex("""(?i)(?:ACTION|ARAÇ):\s*([a-zA-Z0-9_]+)\s*(\([\s\S]*?\)|```json[\s\S]*?```|\{[\s\S]*?\})""")
        val actionMatch = actionRegex.find(text)
        if (actionMatch != null) {
            val toolName = actionMatch.groupValues[1].trim()
            val argsRaw = actionMatch.groupValues[2].trim().removePrefix("```json").removeSuffix("```").trim()
            val argsMap = parseArguments(argsRaw)
            return ParsedTurn(
                thought = thought,
                toolCall = ToolCall(name = toolName, arguments = argsMap, rawAction = actionMatch.value),
                finalAnswer = null
            )
        }

        // 5. Check FINAL_ANSWER: ...
        val finalRegex = Regex("""(?i)(?:FINAL_ANSWER|SONUÇ|CEVAP):\s*([\s\S]*)""")
        val finalMatch = finalRegex.find(text)
        val finalAnswer = if (finalMatch != null) {
            finalMatch.groupValues[1].trim()
        } else if (thought != null && thought.isNotBlank()) {
            // Text without explicit action is treated as answer
            val remainder = text.replace(thoughtMatch?.value.orEmpty(), "").trim()
            if (remainder.isNotEmpty()) remainder else thought
        } else {
            text
        }

        return ParsedTurn(
            thought = thought,
            toolCall = null,
            finalAnswer = finalAnswer
        )
    }

    private fun parseJsonToolCall(jsonStr: String): ToolCall? {
        return try {
            val obj = JSONObject(jsonStr)
            val toolName = obj.optString("tool", "")
            if (toolName.isBlank()) return null
            val args = mutableMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key != "tool") {
                    args[key] = obj.optString(key, "")
                }
            }
            ToolCall(name = toolName, arguments = args, rawAction = jsonStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseArguments(argsRaw: String): Map<String, String> {
        val clean = argsRaw.trim()
        if (clean.startsWith("{") && clean.endsWith("}")) {
            try {
                val obj = JSONObject(clean)
                val map = mutableMapOf<String, String>()
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = obj.optString(k, "")
                }
                return map
            } catch (_: Exception) {}
        }

        // Parse key="value" or key='value'
        val map = mutableMapOf<String, String>()
        val paramRegex = Regex("""([a-zA-Z0-9_]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^,\)]+))""")
        paramRegex.findAll(clean).forEach { match ->
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3].ifEmpty { match.groupValues[4] } }
            map[key] = value.trim()
        }
        return map
    }
}
