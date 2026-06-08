package com.nani.agent.saf

import android.os.Environment
import java.io.File

class BroadFileRepository {
    private val root: File = Environment.getExternalStorageDirectory()

    fun listFiles(limit: Int = 50): List<String> {
        val result = mutableListOf<String>()
        collectEntries(root, prefix = "", result = result, limit = limit)
        return result
    }

    fun summarizeFolder(limit: Int = 50): FolderSummary {
        val files = mutableListOf<String>()
        var fileCount = 0
        var folderCount = 0
        val extensions = mutableMapOf<String, Int>()

        root.walkTopDown().drop(1).forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (file.isDirectory) {
                folderCount += 1
            } else {
                fileCount += 1
                val extension = file.name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                    .ifBlank { "(no extension)" }
                extensions[extension] = (extensions[extension] ?: 0) + 1
                if (files.size < limit) files += relative
            }
        }

        return FolderSummary(
            fileCount = fileCount,
            folderCount = folderCount,
            extensions = extensions.toSortedMap(),
            firstFiles = files
        )
    }

    fun readFile(path: String): String {
        val file = resolveFile(path)
        if (!file.isFile) error("Path is not a file: $path")
        if (file.length() > MAX_TEXT_PREVIEW_BYTES) {
            return "File is larger than 1 MB preview limit. Size: ${file.length()} bytes."
        }
        if (!path.looksTextLike()) return "Binary or unknown file type. Showing metadata only: ${file.name}, ${file.length()} bytes."
        return file.readText(Charsets.UTF_8).take(MAX_TEXT_RESULT_CHARS)
    }

    fun summarizeFile(path: String): String {
        val file = resolveFile(path)
        return "File: ${file.name}\nSize: ${file.length()} bytes\nType: ${file.extension.ifBlank { "unknown" }}"
    }

    fun createFolder(path: String): String {
        val folder = resolveFile(path)
        if (!folder.exists() && !folder.mkdirs()) error("Could not create folder: $path")
        return "Created or found folder: $path"
    }

    fun createFile(path: String, content: String): String {
        val file = uniqueFile(resolveFile(path))
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return "Created file: ${file.relativeTo(root).invariantSeparatorsPath}"
    }

    fun editTextFile(path: String, content: String): String {
        val file = resolveFile(path)
        if (!file.isFile) error("File not found: $path")
        if (file.length() > MAX_TEXT_PREVIEW_BYTES) error("File is larger than 1 MB edit limit: $path")
        if (!path.looksTextLike()) error("Refusing to edit non-text file: $path")
        file.writeText(content, Charsets.UTF_8)
        return "Edited text file: $path"
    }

    fun appendTextFile(path: String, content: String): String {
        val file = resolveFile(path)
        if (!file.isFile) error("File not found: $path")
        if (file.length() > MAX_TEXT_PREVIEW_BYTES) error("File is larger than 1 MB append limit: $path")
        if (!path.looksTextLike()) error("Refusing to edit non-text file: $path")
        file.appendText(content, Charsets.UTF_8)
        return "Appended text file: $path"
    }

    fun copyFile(from: String, to: String): String {
        val source = resolveFile(from)
        if (!source.isFile) error("Source file not found: $from")
        if (source.length() > MAX_COPY_BYTES) error("File is larger than 20 MB MVP copy limit: $from")
        val target = uniqueFile(resolveFile(to))
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = false)
        return "Copied $from to ${target.relativeTo(root).invariantSeparatorsPath}"
    }

    fun renameFile(from: String, to: String): String {
        val source = resolveFile(from)
        if (!source.exists()) error("Source not found: $from")
        val target = resolveFile(to)
        if (source.parentFile?.canonicalPath != target.parentFile?.canonicalPath) {
            error("Rename cannot move files between folders.")
        }
        if (target.exists()) error("Target already exists: $to")
        if (!source.renameTo(target)) error("Rename failed: $from")
        return "Renamed $from to $to"
    }

    fun searchFiles(query: String, limit: Int = 50): List<String> {
        val normalized = query.lowercase()
        return listFiles(limit = 500).filter { it.lowercase().contains(normalized) }.take(limit)
    }

    fun classifyFiles(limit: Int = 50): List<String> {
        return listFiles(limit = limit).map { path ->
            val extension = path.substringAfterLast('.', missingDelimiterValue = "").ifBlank { "folder-or-unknown" }
            "$path -> $extension"
        }
    }

    private fun collectEntries(folder: File, prefix: String, result: MutableList<String>, limit: Int) {
        if (result.size >= limit) return
        folder.listFiles().orEmpty().forEach { child ->
            if (result.size >= limit) return
            val path = if (prefix.isBlank()) child.name else "$prefix/${child.name}"
            result += if (child.isDirectory) "$path/" else path
            if (child.isDirectory) collectEntries(child, path, result, limit)
        }
    }

    private fun resolveFile(path: String): File {
        val file = File(root, path.replace("\\", "/"))
        val rootPath = root.canonicalPath
        val filePath = file.canonicalPath
        if (filePath != rootPath && !filePath.startsWith(rootPath + File.separator)) {
            error("Path escapes broad storage root: $path")
        }
        return file
    }

    private fun uniqueFile(requested: File): File {
        if (!requested.exists()) return requested
        val parent = requested.parentFile ?: root
        val name = requested.name
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
        var index = 1
        while (true) {
            val candidateName = if (extension.isBlank()) "$base ($index)" else "$base ($index).$extension"
            val candidate = File(parent, candidateName)
            if (!candidate.exists()) return candidate
            index += 1
        }
    }

    companion object {
        private const val MAX_COPY_BYTES = 20L * 1024L * 1024L
        private const val MAX_TEXT_PREVIEW_BYTES = 1L * 1024L * 1024L
        private const val MAX_TEXT_RESULT_CHARS = 32_000
    }
}

private fun String.looksTextLike(): Boolean {
    val lower = lowercase()
    return lower.endsWith(".txt") ||
        lower.endsWith(".md") ||
        lower.endsWith(".csv") ||
        lower.endsWith(".json") ||
        lower.endsWith(".xml") ||
        lower.endsWith(".html") ||
        lower.endsWith(".kt") ||
        lower.endsWith(".java")
}
