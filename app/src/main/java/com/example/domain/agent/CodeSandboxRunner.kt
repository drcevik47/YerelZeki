package com.example.domain.agent

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class CodeExecutionResult(
    val language: String,
    val output: String,
    val isSuccess: Boolean,
    val executionTimeMs: Long
)

class CodeSandboxRunner(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun execute(language: String, code: String): CodeExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val lang = language.lowercase().trim()

        when (lang) {
            "javascript", "js", "web" -> executeJavaScript(code, startTime)
            "math", "calc" -> executeMathCalculation(code, startTime)
            else -> executeJavaScript(code, startTime)
        }
    }

    private suspend fun executeJavaScript(code: String, startTime: Long): CodeExecutionResult {
        val resultDeferred = CompletableDeferred<CodeExecutionResult>()

        mainHandler.post {
            try {
                val webView = WebView(context).apply {
                    settings.javaScriptEnabled = true
                }

                val logs = StringBuilder()
                val jsBridge = object {
                    @JavascriptInterface
                    fun log(message: String) {
                        logs.append(message).append("\n")
                    }

                    @JavascriptInterface
                    fun error(message: String) {
                        logs.append("[HATA] ").append(message).append("\n")
                    }

                    @JavascriptInterface
                    fun finish(result: String) {
                        val duration = System.currentTimeMillis() - startTime
                        val fullOutput = if (logs.isNotEmpty()) {
                            "Çıktı (Console):\n$logs\nDönen Değer: $result"
                        } else {
                            "Dönen Değer: $result"
                        }
                        resultDeferred.complete(
                            CodeExecutionResult(
                                language = "javascript",
                                output = fullOutput.trim(),
                                isSuccess = true,
                                executionTimeMs = duration
                            )
                        )
                        webView.destroy()
                    }
                }

                webView.addJavascriptInterface(jsBridge, "AndroidBridge")

                // Wrap user code to intercept console.log and capture return value
                val wrappedHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head><meta charset="utf-8"></head>
                    <body>
                    <script>
                        (function() {
                            console.log = function() {
                                var args = Array.prototype.slice.call(arguments);
                                AndroidBridge.log(args.map(a => typeof a === 'object' ? JSON.stringify(a) : a).join(' '));
                            };
                            console.error = function() {
                                var args = Array.prototype.slice.call(arguments);
                                AndroidBridge.error(args.map(a => typeof a === 'object' ? JSON.stringify(a) : a).join(' '));
                            };
                            try {
                                var ret = (function() {
                                    $code
                                })();
                                AndroidBridge.finish(ret !== undefined ? String(ret) : "(void/tamamlandı)");
                            } catch (e) {
                                AndroidBridge.error(e.message || String(e));
                                AndroidBridge.finish("HATA: " + (e.message || String(e)));
                            }
                        })();
                    </script>
                    </body>
                    </html>
                """.trimIndent()

                webView.loadDataWithBaseURL("https://sandbox.local/", wrappedHtml, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                resultDeferred.complete(
                    CodeExecutionResult(
                        language = "javascript",
                        output = "WebView Sandbox başlatılamadı: ${e.message}",
                        isSuccess = false,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                )
            }
        }

        // Wait up to 8 seconds for JS execution
        val result = withTimeoutOrNull(8000) {
            resultDeferred.await()
        }

        return result ?: CodeExecutionResult(
            language = "javascript",
            output = "Zaman aşımı: Kod 8 saniyeden uzun sürdü.",
            isSuccess = false,
            executionTimeMs = 8000
        )
    }

    private fun executeMathCalculation(expression: String, startTime: Long): CodeExecutionResult {
        return try {
            // Simple robust math parser
            val clean = expression.replace(" ", "")
            val res = evaluateSimpleMath(clean)
            CodeExecutionResult(
                language = "math",
                output = "Hesaplama Sonucu: $res",
                isSuccess = true,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            CodeExecutionResult(
                language = "math",
                output = "Hesaplama hatası: ${e.message}",
                isSuccess = false,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    private fun evaluateSimpleMath(expr: String): Double {
        return when {
            expr.contains("+") -> {
                val parts = expr.split("+")
                parts.sumOf { evaluateSimpleMath(it) }
            }
            expr.contains("-") && !expr.startsWith("-") -> {
                val parts = expr.split("-")
                var res = evaluateSimpleMath(parts[0])
                for (i in 1 until parts.size) {
                    res -= evaluateSimpleMath(parts[i])
                }
                res
            }
            expr.contains("*") -> {
                val parts = expr.split("*")
                parts.map { evaluateSimpleMath(it) }.fold(1.0) { a, b -> a * b }
            }
            expr.contains("/") -> {
                val parts = expr.split("/")
                var res = evaluateSimpleMath(parts[0])
                for (i in 1 until parts.size) {
                    val denom = evaluateSimpleMath(parts[i])
                    if (denom == 0.0) throw ArithmeticException("Sıfıra bölme hatası")
                    res /= denom
                }
                res
            }
            else -> expr.toDouble()
        }
    }
}
