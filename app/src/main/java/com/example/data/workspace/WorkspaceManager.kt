package com.example.data.workspace

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class WorkspaceFile(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String,
    val canRead: Boolean = true,
    val canWrite: Boolean = true
)

data class FileMetadata(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lineCount: Int,
    val isDirectory: Boolean,
    val lastModified: Long,
    val canRead: Boolean,
    val canWrite: Boolean
)

class WorkspaceManager(private val context: Context) {

    // 1. App-private Internal Workspace (Secure by default)
    val internalWorkspaceDir: File by lazy {
        val dir = File(context.filesDir, "workspace")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    // 2. App-specific External Storage
    val externalWorkspaceDir: File? by lazy {
        context.getExternalFilesDir("workspace")?.also {
            if (!it.exists()) it.mkdirs()
        }
    }

    // 3. Allowed root directories
    private val allowedRoots: List<File> by lazy {
        val list = mutableListOf(internalWorkspaceDir)
        externalWorkspaceDir?.let { list.add(it) }
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloads != null && downloads.exists()) {
                list.add(downloads)
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun initializeWorkspaceIfNeeded() = withContext(Dispatchers.IO) {
        val defaultProjectDir = File(internalWorkspaceDir, "demo_project")
        if (!defaultProjectDir.exists() || defaultProjectDir.list()?.isEmpty() == true) {
            defaultProjectDir.mkdirs()

            // Seed sample files
            File(defaultProjectDir, "index.html").writeText(
                """<!DOCTYPE html>
<html lang="tr">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Gemma Agent Demo</title>
  <style>
    body { font-family: system-ui, sans-serif; background: #0f172a; color: #f8fafc; padding: 24px; text-align: center; }
    .card { background: #1e293b; border-radius: 12px; padding: 20px; max-width: 480px; margin: 0 auto; box-shadow: 0 4px 20px rgba(0,0,0,0.4); }
    h1 { color: #38bdf8; font-size: 22px; margin-bottom: 8px; }
    p { color: #94a3b8; font-size: 14px; line-height: 1.5; }
    .btn { background: #38bdf8; color: #0f172a; border: none; padding: 10px 18px; border-radius: 8px; font-weight: bold; cursor: pointer; margin-top: 12px; }
    .btn:hover { background: #7dd3fc; }
    #counter { font-size: 28px; color: #a855f7; font-weight: bold; margin: 12px 0; }
  </style>
</head>
<body>
  <div class="card">
    <h1>🚀 Gemma 3n Agent Projesi</h1>
    <p>Bu dosya Gemma Agent tarafından doğrudan telefonda oluşturulabilir, okunabilir ve düzenlenebilir!</p>
    <div id="counter">0</div>
    <button class="btn" onclick="increment()">Sayaç Arttır</button>
  </div>
  <script>
    let count = 0;
    function increment() {
      count++;
      document.getElementById('counter').innerText = count;
      console.log('Sayaç güncellendi: ' + count);
    }
  </script>
</body>
</html>"""
            )

            File(defaultProjectDir, "algorithm.py").writeText(
                """# Gemma Agent Python Örneği
def fibonacci(n: int) -> list[int]:
    \"\"\"İlk n Fibonacci sayısını hesaplar.\"\"\"
    if n <= 0:
        return []
    sequence = [0, 1]
    while len(sequence) < n:
        sequence.append(sequence[-1] + sequence[-2])
    return sequence[:n]

if __name__ == "__main__":
    nums = fibonacci(10)
    print("Fibonacci İlk 10 Sayı:", nums)
"""
            )

            File(defaultProjectDir, "notes.txt").writeText(
                """# Gemma Agent Dosya Sistemi Notları
1. Agent Android dosya sisteminde okuma, yazma, düzenleme ve silme yapabilir.
2. Desteklenen diller: Python, JavaScript, Java, Kotlin, HTML/CSS, SQL.
3. Güvenlik: Uygulama korumalı çalışma alanında dosya işlemleri yürütür."""
            )
        }
    }

    /**
     * Resolves a path safely.
     * If relative, resolves against internalWorkspaceDir.
     * If absolute, checks whether it is inside permitted roots.
     */
    fun resolvePath(pathStr: String): File {
        val clean = pathStr.trim()
        val candidate = if (clean.startsWith("/")) {
            File(clean)
        } else {
            File(internalWorkspaceDir, clean.trimStart('/', '\\'))
        }

        // Canonical verification for security against path traversal
        val canonical = candidate.canonicalFile
        val isAllowed = allowedRoots.any { root ->
            canonical.path.startsWith(root.canonicalPath)
        }

        // Allow app filesDir, cacheDir, externalFilesDir
        val isInAppDirs = canonical.path.startsWith(context.filesDir.canonicalPath) ||
                (context.getExternalFilesDir(null) != null && canonical.path.startsWith(context.getExternalFilesDir(null)!!.canonicalPath))

        if (!isAllowed && !isInAppDirs) {
            // If user specified a relative or sandbox path, clamp to internal workspace
            return File(internalWorkspaceDir, candidate.name)
        }

        return canonical
    }

    suspend fun listFiles(relativePath: String = "", recursive: Boolean = false): List<WorkspaceFile> =
        withContext(Dispatchers.IO) {
            val targetDir = resolvePath(relativePath)
            if (!targetDir.exists() || !targetDir.isDirectory) {
                return@withContext emptyList()
            }

            val result = mutableListOf<WorkspaceFile>()
            val files = targetDir.listFiles()?.sortedWith(
                compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: emptyList()

            for (file in files) {
                val relPath = try {
                    file.relativeTo(internalWorkspaceDir).path
                } catch (_: Exception) {
                    file.name
                }

                result.add(
                    WorkspaceFile(
                        name = file.name,
                        relativePath = relPath,
                        absolutePath = file.absolutePath,
                        isDirectory = file.isDirectory,
                        sizeBytes = if (file.isDirectory) 0 else file.length(),
                        lastModified = file.lastModified(),
                        extension = file.extension.lowercase(),
                        canRead = file.canRead(),
                        canWrite = file.canWrite()
                    )
                )
                if (recursive && file.isDirectory) {
                    result.addAll(listFiles(file.absolutePath, recursive = true))
                }
            }
            result
        }

    suspend fun readFile(pathStr: String): String = withContext(Dispatchers.IO) {
        val file = resolvePath(pathStr)
        if (!file.exists()) {
            throw IOException("Dosya bulunamadı: $pathStr (${file.absolutePath})")
        }
        if (file.isDirectory) {
            throw IOException("Yol bir klasördür, dosya değil: $pathStr")
        }
        if (!file.canRead()) {
            throw SecurityException("Dosyayı okuma izni yok: $pathStr")
        }
        file.readText(Charsets.UTF_8)
    }

    suspend fun writeFile(pathStr: String, content: String, overwrite: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val file = resolvePath(pathStr)
            if (file.exists() && !overwrite) {
                throw IllegalStateException("Dosya zaten var ve üzerine yazma devre dışı: $pathStr")
            }
            file.parentFile?.mkdirs()
            file.writeText(content, Charsets.UTF_8)
            "Dosya başarıyla yazıldı: ${file.name} (${file.length()} bayt, ${file.absolutePath})"
        }

    suspend fun editFile(pathStr: String, targetContent: String, replacementContent: String): String =
        withContext(Dispatchers.IO) {
            val file = resolvePath(pathStr)
            if (!file.exists()) {
                throw IOException("Düzenlenecek dosya bulunamadı: $pathStr")
            }
            val original = file.readText(Charsets.UTF_8)
            if (!original.contains(targetContent)) {
                throw IllegalArgumentException(
                    "Hedef metin dosyada bulunamadı. Lütfen tam eşleştiğinden emin olun:\n$targetContent"
                )
            }
            val updated = original.replaceFirst(targetContent, replacementContent)
            file.writeText(updated, Charsets.UTF_8)
            "Dosya başarıyla güncellendi: ${file.name}"
        }

    suspend fun deleteFile(pathStr: String): String = withContext(Dispatchers.IO) {
        val file = resolvePath(pathStr)
        if (!file.exists()) {
            throw IOException("Silinecek dosya bulunamadı: $pathStr")
        }
        val isDir = file.isDirectory
        val deleted = file.deleteRecursively()
        if (deleted) {
            if (isDir) "Klasör başarıyla silindi: ${file.name}" else "Dosya başarıyla silindi: ${file.name}"
        } else {
            throw IOException("Dosya silinemedi: $pathStr")
        }
    }

    suspend fun createDirectory(pathStr: String): String = withContext(Dispatchers.IO) {
        val dir = resolvePath(pathStr)
        if (dir.exists()) {
            return@withContext "Klasör zaten mevcut: ${dir.name}"
        }
        if (dir.mkdirs()) {
            "Klasör oluşturuldu: ${dir.name} (${dir.absolutePath})"
        } else {
            throw IOException("Klasör oluşturulamadı: $pathStr")
        }
    }

    suspend fun getFileMetadata(pathStr: String): FileMetadata = withContext(Dispatchers.IO) {
        val file = resolvePath(pathStr)
        if (!file.exists()) throw IOException("Dosya mevcut değil: $pathStr")
        val lines = if (!file.isDirectory && file.length() < 1_000_000) {
            try { file.readLines().size } catch (_: Exception) { 0 }
        } else 0

        FileMetadata(
            path = file.absolutePath,
            name = file.name,
            sizeBytes = file.length(),
            lineCount = lines,
            isDirectory = file.isDirectory,
            lastModified = file.lastModified(),
            canRead = file.canRead(),
            canWrite = file.canWrite()
        )
    }

    suspend fun searchFiles(query: String, extensionFilter: String? = null): List<WorkspaceFile> =
        withContext(Dispatchers.IO) {
            val all = listFiles("", recursive = true)
            all.filter { file ->
                val matchesExt = extensionFilter == null || file.extension.equals(extensionFilter.trimStart('.'), ignoreCase = true)
                val matchesName = file.name.contains(query, ignoreCase = true)
                val matchesContent = if (!file.isDirectory && file.sizeBytes < 250_000) {
                    try {
                        File(file.absolutePath).readText(Charsets.UTF_8).contains(query, ignoreCase = true)
                    } catch (e: Exception) {
                        false
                    }
                } else false

                matchesExt && (matchesName || matchesContent)
            }
        }
}
