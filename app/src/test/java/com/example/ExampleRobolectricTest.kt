package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.workspace.WorkspaceManager
import com.example.domain.agent.ReActParser
import com.example.domain.llm.HardwareAccelerationManager
import com.example.domain.llm.HardwareBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Gemma Agent", appName)
    }

    @Test
    fun `test workspace file operations`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WorkspaceManager(context)

        // Write file
        val writeMsg = manager.writeFile("test_folder/sample.py", "print('hello from gemma')", overwrite = true)
        assertTrue(writeMsg.contains("başarıyla yazıldı"))

        // Read file
        val content = manager.readFile("test_folder/sample.py")
        assertEquals("print('hello from gemma')", content)

        // Edit file
        manager.editFile("test_folder/sample.py", "hello", "merhaba")
        val updatedContent = manager.readFile("test_folder/sample.py")
        assertEquals("print('merhaba from gemma')", updatedContent)

        // List files
        val files = manager.listFiles("test_folder", recursive = false)
        assertTrue(files.any { it.name == "sample.py" })

        // Delete file
        val delMsg = manager.deleteFile("test_folder/sample.py")
        assertTrue(delMsg.contains("başarıyla silindi"))
    }

    @Test
    fun `test react parser tool calls`() {
        val sampleOutput = """
THOUGHT: Kullanıcının istediği dosyayı yazmam gerekiyor.
ACTION: ```json
{"tool": "write_file", "path": "demo.js", "content": "console.log(42);"}
```
""".trimIndent()

        val parsed = ReActParser.parse(sampleOutput)
        assertNotNull(parsed.thought)
        assertTrue(parsed.thought!!.contains("dosyayı yazmam gerekiyor"))
        assertNotNull(parsed.toolCall)
        assertEquals("write_file", parsed.toolCall!!.name)
        assertEquals("demo.js", parsed.toolCall!!.arguments["path"])
        assertEquals("console.log(42);", parsed.toolCall!!.arguments["content"])
    }

    @Test
    fun `test hardware diagnostics`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val hwManager = HardwareAccelerationManager(context)
        val info = hwManager.getDeviceInfo()
        assertNotNull(info.hardwareName)
        assertTrue(info.totalRamMb >= 0)
        assertNotNull(info.recommendedBackend)
    }
}
