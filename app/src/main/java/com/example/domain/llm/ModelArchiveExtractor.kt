package com.example.domain.llm

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

sealed class ExtractionState {
    data class Progress(val percentage: Float, val currentFile: String, val bytesExtracted: Long) : ExtractionState()
    data class Success(val extractedModelFile: File, val totalBytes: Long) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

class ModelArchiveExtractor(private val context: Context) {

    val modelsDir: File by lazy {
        val dir = File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * Extracts a model archive (.tar.gz, .tgz, .zip, or directly imports .bin/.task/.tflite)
     * into context.filesDir/models/
     */
    fun extractArchiveStream(sourceUri: Uri, originalFilename: String): Flow<ExtractionState> = flow {
        try {
            val nameLower = originalFilename.lowercase()

            when {
                nameLower.endsWith(".tar.gz") || nameLower.endsWith(".tgz") -> {
                    emit(ExtractionState.Progress(0.05f, "GZIP Arşivi açılıyor...", 0))
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                        ?: throw IOException("Dosya açılamadı: $sourceUri")

                    extractTarGz(inputStream) { progress, file, bytes ->
                        emit(ExtractionState.Progress(progress, file, bytes))
                    }.let { extractedModel ->
                        emit(ExtractionState.Success(extractedModel, extractedModel.length()))
                    }
                }
                nameLower.endsWith(".zip") -> {
                    emit(ExtractionState.Progress(0.05f, "ZIP Arşivi açılıyor...", 0))
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                        ?: throw IOException("Dosya açılamadı: $sourceUri")

                    extractZip(inputStream) { progress, file, bytes ->
                        emit(ExtractionState.Progress(progress, file, bytes))
                    }.let { extractedModel ->
                        emit(ExtractionState.Success(extractedModel, extractedModel.length()))
                    }
                }
                nameLower.endsWith(".bin") || nameLower.endsWith(".task") || nameLower.endsWith(".tflite") -> {
                    emit(ExtractionState.Progress(0.1f, "Model doğrudan kopyalanıyor: $originalFilename", 0))
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                        ?: throw IOException("Dosya açılamadı: $sourceUri")

                    val destFile = File(modelsDir, originalFilename)
                    copyStreamWithProgress(inputStream, destFile) { progress, bytes ->
                        emit(ExtractionState.Progress(progress, originalFilename, bytes))
                    }
                    emit(ExtractionState.Success(destFile, destFile.length()))
                }
                else -> {
                    // Try as tar.gz by default
                    emit(ExtractionState.Progress(0.05f, "Arşiv formatı çözümleniyor...", 0))
                    val inputStream = context.contentResolver.openInputStream(sourceUri)
                        ?: throw IOException("Dosya açılamadı: $sourceUri")
                    try {
                        val model = extractTarGz(inputStream) { progress, file, bytes ->
                            emit(ExtractionState.Progress(progress, file, bytes))
                        }
                        emit(ExtractionState.Success(model, model.length()))
                    } catch (e: Exception) {
                        // Fallback: direct copy
                        val destFile = File(modelsDir, originalFilename)
                        context.contentResolver.openInputStream(sourceUri)?.use { s ->
                            destFile.outputStream().use { d -> s.copyTo(d) }
                        }
                        emit(ExtractionState.Success(destFile, destFile.length()))
                    }
                }
            }
        } catch (e: Exception) {
            emit(ExtractionState.Error("Model çıkarma hatası: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Extracts .tar.gz without external dependencies.
     * Tar header structure: 512-byte blocks.
     */
    private suspend fun extractTarGz(
        sourceStream: InputStream,
        onProgress: suspend (Float, String, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val gzipStream = GZIPInputStream(BufferedInputStream(sourceStream, 65536))
        var candidateModelFile: File? = null
        var totalBytesWritten = 0L

        val header = ByteArray(512)
        while (true) {
            val bytesRead = readFully(gzipStream, header)
            if (bytesRead < 512) break

            // Check if end of archive (empty block)
            if (header.all { it == 0.toByte() }) {
                break
            }

            // Extract filename from bytes 0..99
            val rawName = String(header, 0, 100, Charsets.US_ASCII).trim('\u0000', ' ')
            if (rawName.isEmpty()) continue

            // Clean path
            val entryName = File(rawName).name

            // Extract file size from bytes 124..135 (octal ASCII)
            val sizeStr = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ')
            val fileSize = try {
                sizeStr.toLong(8)
            } catch (_: Exception) {
                0L
            }

            // Type flag at byte 156 ('0' or '\0' is regular file, '5' is directory)
            val typeFlag = header[156].toInt().toChar()
            val isDirectory = typeFlag == '5' || rawName.endsWith("/")

            if (isDirectory) {
                File(modelsDir, entryName).mkdirs()
                continue
            }

            val targetFile = File(modelsDir, entryName)
            targetFile.parentFile?.mkdirs()

            // Stream file contents
            targetFile.outputStream().use { out ->
                var remaining = fileSize
                val buffer = ByteArray(65536)
                while (remaining > 0) {
                    val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                    val count = gzipStream.read(buffer, 0, toRead)
                    if (count < 0) break
                    out.write(buffer, 0, count)
                    remaining -= count
                    totalBytesWritten += count
                }
            }

            // In TAR format, file contents are padded to 512-byte boundaries
            val padding = (512 - (fileSize % 512)) % 512
            if (padding > 0) {
                gzipStream.skip(padding)
            }

            onProgress(0.5f, entryName, totalBytesWritten)

            val lower = entryName.lowercase()
            if (lower.endsWith(".bin") || lower.endsWith(".task") || lower.endsWith(".tflite")) {
                candidateModelFile = targetFile
            }
        }

        candidateModelFile ?: findBestModelInDir() ?: throw IOException("Arşiv içinde geçerli .bin/.task/.tflite modeli bulunamadı.")
    }

    private suspend fun extractZip(
        sourceStream: InputStream,
        onProgress: suspend (Float, String, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val zipStream = ZipInputStream(BufferedInputStream(sourceStream, 65536))
        var candidateModelFile: File? = null
        var totalBytes = 0L

        var entry = zipStream.nextEntry
        while (entry != null) {
            val fileName = File(entry.name).name
            if (!entry.isDirectory && fileName.isNotBlank()) {
                val targetFile = File(modelsDir, fileName)
                targetFile.parentFile?.mkdirs()

                targetFile.outputStream().use { out ->
                    val buffer = ByteArray(65536)
                    var count: Int
                    while (zipStream.read(buffer).also { count = it } != -1) {
                        out.write(buffer, 0, count)
                        totalBytes += count
                    }
                }
                onProgress(0.5f, fileName, totalBytes)

                val lower = fileName.lowercase()
                if (lower.endsWith(".bin") || lower.endsWith(".task") || lower.endsWith(".tflite")) {
                    candidateModelFile = targetFile
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }

        candidateModelFile ?: findBestModelInDir() ?: throw IOException("ZIP içinde model dosyası bulunamadı.")
    }

    private suspend fun copyStreamWithProgress(
        sourceStream: InputStream,
        destFile: File,
        onProgress: suspend (Float, Long) -> Unit
    ) {
        sourceStream.use { input ->
            destFile.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var count: Int
                var total = 0L
                while (input.read(buffer).also { count = it } != -1) {
                    output.write(buffer, 0, count)
                    total += count
                    onProgress(0.5f, total)
                }
            }
        }
    }

    private fun findBestModelInDir(): File? {
        val files = modelsDir.listFiles() ?: return null
        return files.firstOrNull {
            it.isFile && it.length() > 50_000_000 &&
            (it.extension in listOf("bin", "task", "tflite") || it.name.contains("gemma", ignoreCase = true))
        } ?: files.maxByOrNull { it.length() }
    }

    private fun readFully(stream: InputStream, b: ByteArray): Int {
        var offset = 0
        while (offset < b.size) {
            val count = stream.read(b, offset, b.size - offset)
            if (count < 0) break
            offset += count
        }
        return offset
    }
}
