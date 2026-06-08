package com.nani.agent.saf

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class SafFileRepository(
    private val context: Context,
    rootUri: Uri
) {
    private val root: DocumentFile = requireNotNull(DocumentFile.fromTreeUri(context, rootUri)) {
        "Unable to open selected work folder."
    }

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

        fun walk(folder: DocumentFile, prefix: String) {
            folder.listFiles().forEach { child ->
                val name = child.name.orEmpty()
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                if (child.isDirectory) {
                    folderCount += 1
                    walk(child, path)
                } else {
                    fileCount += 1
                    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
                        .ifBlank { "(no extension)" }
                    extensions[extension] = (extensions[extension] ?: 0) + 1
                    if (files.size < limit) files += path
                }
            }
        }

        walk(root, "")
        return FolderSummary(
            fileCount = fileCount,
            folderCount = folderCount,
            extensions = extensions.toSortedMap(),
            firstFiles = files
        )
    }

    fun createFolder(path: String): String {
        val folder = ensureFolder(path)
        return "Created or found folder: ${folder.name ?: path}"
    }

    fun copyFile(from: String, to: String): String {
        val source = findRelativeFile(from)
            ?: error("Source file not found: $from")
        if (!source.isFile) error("Source is not a file: $from")

        val sourceLength = source.length()
        if (sourceLength > MAX_COPY_BYTES) {
            error("File is larger than 20 MB MVP copy limit: $from")
        }

        val targetFolderPath = to.substringBeforeLast('/', missingDelimiterValue = "")
        val requestedName = to.substringAfterLast('/')
        if (requestedName.isBlank()) error("Target file name is missing: $to")

        val targetFolder = if (targetFolderPath.isBlank()) root else ensureFolder(targetFolderPath)
        val targetName = uniqueName(targetFolder, requestedName)
        val target = targetFolder.createFile(source.type ?: "application/octet-stream", targetName)
            ?: error("Could not create target file: $to")

        val resolver = context.contentResolver
        resolver.openInputStream(source.uri).use { input ->
            resolver.openOutputStream(target.uri, "wt").use { output ->
                if (input == null || output == null) error("Could not open copy streams.")
                input.copyTo(output)
            }
        }

        return "Copied $from to ${if (targetFolderPath.isBlank()) targetName else "$targetFolderPath/$targetName"}"
    }

    private fun collectEntries(folder: DocumentFile, prefix: String, result: MutableList<String>, limit: Int) {
        if (result.size >= limit) return
        folder.listFiles().forEach { child ->
            if (result.size >= limit) return
            val name = child.name.orEmpty()
            val path = if (prefix.isBlank()) name else "$prefix/$name"
            result += if (child.isDirectory) "$path/" else path
            if (child.isDirectory) collectEntries(child, path, result, limit)
        }
    }

    private fun ensureFolder(path: String): DocumentFile {
        return path.split('/')
            .filter { it.isNotBlank() }
            .fold(root) { folder, segment ->
                folder.findFile(segment)?.takeIf { it.isDirectory }
                    ?: folder.createDirectory(segment)
                    ?: error("Could not create folder segment: $segment")
            }
    }

    private fun findRelativeFile(path: String): DocumentFile? {
        return path.split('/')
            .filter { it.isNotBlank() }
            .fold(root as DocumentFile?) { current, segment ->
                current?.findFile(segment)
            }
    }

    private fun uniqueName(folder: DocumentFile, requestedName: String): String {
        val base = requestedName.substringBeforeLast('.', requestedName)
        val extension = requestedName.substringAfterLast('.', missingDelimiterValue = "")
        fun withIndex(index: Int): String {
            val suffix = if (index == 0) "" else " ($index)"
            return if (extension.isBlank()) "$base$suffix" else "$base$suffix.$extension"
        }

        var index = 0
        while (folder.findFile(withIndex(index)) != null) {
            index += 1
        }
        return withIndex(index)
    }

    companion object {
        private const val MAX_COPY_BYTES = 20L * 1024L * 1024L
    }
}

data class FolderSummary(
    val fileCount: Int,
    val folderCount: Int,
    val extensions: Map<String, Int>,
    val firstFiles: List<String>
)
