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

    fun readFile(path: String): String {
        val file = findRelativeFile(path) ?: error("File not found: $path")
        if (!file.isFile) error("Path is not a file: $path")
        if (file.length() > MAX_TEXT_PREVIEW_BYTES) {
            return "File is larger than 1 MB preview limit. Size: ${file.length()} bytes."
        }
        val type = file.type.orEmpty()
        if (type.isNotBlank() && !type.startsWith("text/") && !path.looksTextLike()) {
            return "Binary or unknown file type. Showing metadata only: ${file.name}, ${file.length()} bytes."
        }
        val text = context.contentResolver.openInputStream(file.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Could not read file: $path")
        return text.take(MAX_TEXT_RESULT_CHARS)
    }

    fun summarizeFile(path: String): String {
        val file = findRelativeFile(path) ?: error("File not found: $path")
        return "File: ${file.name ?: path}\nSize: ${file.length()} bytes\nType: ${file.type ?: "unknown"}"
    }

    fun createFile(path: String, content: String): String {
        val folderPath = path.substringBeforeLast('/', missingDelimiterValue = "")
        val requestedName = path.substringAfterLast('/')
        if (requestedName.isBlank()) error("File name is missing: $path")
        val folder = if (folderPath.isBlank()) root else ensureFolder(folderPath)
        val targetName = uniqueName(folder, requestedName)
        val target = folder.createFile(mimeTypeForName(targetName), targetName)
            ?: error("Could not create file: $path")
        context.contentResolver.openOutputStream(target.uri, "wt").use { output ->
            output ?: error("Could not write file: $path")
            output.write(content.toByteArray(Charsets.UTF_8))
        }
        return "Created file: ${if (folderPath.isBlank()) targetName else "$folderPath/$targetName"}"
    }

    fun editTextFile(path: String, content: String): String {
        val file = findRelativeFile(path) ?: error("File not found: $path")
        if (!file.isFile) error("Path is not a file: $path")
        if (file.length() > MAX_TEXT_PREVIEW_BYTES) error("File is larger than 1 MB edit limit: $path")
        if (!file.type.orEmpty().startsWith("text/") && !path.looksTextLike()) {
            error("Refusing to edit non-text file: $path")
        }
        context.contentResolver.openOutputStream(file.uri, "wt").use { output ->
            output ?: error("Could not write file: $path")
            output.write(content.toByteArray(Charsets.UTF_8))
        }
        return "Edited text file: $path"
    }

    fun appendTextFile(path: String, content: String): String {
        val current = readFile(path)
        return editTextFile(path, current + content)
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

    fun renameFile(from: String, to: String): String {
        val source = findRelativeFile(from) ?: error("Source not found: $from")
        val sourceFolderPath = from.substringBeforeLast('/', missingDelimiterValue = "")
        val targetFolderPath = to.substringBeforeLast('/', missingDelimiterValue = "")
        if (sourceFolderPath != targetFolderPath) {
            error("Rename cannot move files between folders.")
        }
        val targetName = to.substringAfterLast('/')
        if (targetName.isBlank()) error("Target name is missing: $to")
        val folder = if (sourceFolderPath.isBlank()) root else ensureFolder(sourceFolderPath)
        if (folder.findFile(targetName) != null) error("Target already exists: $to")
        if (!source.renameTo(targetName)) error("Rename failed: $from")
        return "Renamed $from to $to"
    }

    fun searchFiles(query: String, limit: Int = 50): List<String> {
        val normalized = query.lowercase()
        return listFiles(limit = 500)
            .filter { it.lowercase().contains(normalized) }
            .take(limit)
    }

    fun classifyFiles(limit: Int = 50): List<String> {
        return listFiles(limit = limit).map { path ->
            val extension = path.substringAfterLast('.', missingDelimiterValue = "").ifBlank { "folder-or-unknown" }
            "$path -> $extension"
        }
    }

    fun listFileEntries(limit: Int = Int.MAX_VALUE): List<SafFileEntry> {
        val result = mutableListOf<SafFileEntry>()
        fun walk(folder: DocumentFile, prefix: String) {
            if (result.size >= limit) return
            folder.listFiles().forEach { child ->
                if (result.size >= limit) return
                val name = child.name.orEmpty()
                val path = if (prefix.isBlank()) name else "$prefix/$name"
                if (child.isDirectory) {
                    walk(child, path)
                } else if (child.isFile) {
                    result += SafFileEntry(
                        path = path,
                        name = name,
                        extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(),
                        lastModified = child.lastModified(),
                        size = child.length()
                    )
                }
            }
        }
        walk(root, "")
        return result
    }

    fun batchGroupFiles(
        groupSize: Int = 25,
        targetFolderPrefix: String = "W",
        fileTypes: List<String> = listOf("jpg", "jpeg", "png"),
        mode: String = "copy",
        sortBy: String = "name",
        shouldContinue: () -> Boolean = { true }
    ): BatchGroupResult {
        if (mode.lowercase() != "copy") {
            error("batch_group_files only supports copy mode. Originals are always kept.")
        }
        if (groupSize <= 0) error("groupSize must be greater than zero.")
        if (targetFolderPrefix.contains('/') || targetFolderPrefix.contains('\\') || targetFolderPrefix.contains(':')) {
            error("targetFolderPrefix must be a simple folder-name prefix.")
        }
        val normalizedTypes = fileTypes
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("jpg", "jpeg", "png") }
            .toSet()
        val prefix = targetFolderPrefix.ifBlank { "W" }
        val targetFolderPattern = Regex("^${Regex.escape(prefix)}-\\d+/")
        val files = listFileEntries()
            .asSequence()
            .filter { it.extension in normalizedTypes }
            .filterNot { targetFolderPattern.containsMatchIn(it.path) }
            .toList()
            .sortedWith(
                if (sortBy.lowercase() == "date") {
                    compareBy<SafFileEntry> { it.lastModified }.then(naturalNameComparator())
                } else {
                    naturalNameComparator()
                }
            )

        val copied = mutableListOf<String>()
        files.chunked(groupSize).forEachIndexed { index, chunk ->
            if (!shouldContinue()) error("Batch grouping paused or stopped.")
            val folderName = "$prefix-${index + 1}"
            createFolder(folderName)
            chunk.forEach { file ->
                if (!shouldContinue()) error("Batch grouping paused or stopped.")
                copyFile(file.path, "$folderName/${file.name}")
                copied += "${file.path} -> $folderName/${file.name}"
            }
        }

        return BatchGroupResult(
            matchedFiles = files.size,
            createdGroups = if (files.isEmpty()) 0 else ((files.size - 1) / groupSize) + 1,
            copiedFiles = copied.size,
            examples = copied.take(20)
        )
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
        private const val MAX_TEXT_PREVIEW_BYTES = 1L * 1024L * 1024L
        private const val MAX_TEXT_RESULT_CHARS = 32_000
    }
}

data class SafFileEntry(
    val path: String,
    val name: String,
    val extension: String,
    val lastModified: Long,
    val size: Long
)

data class BatchGroupResult(
    val matchedFiles: Int,
    val createdGroups: Int,
    val copiedFiles: Int,
    val examples: List<String>
)

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

private fun mimeTypeForName(name: String): String {
    return if (name.looksTextLike()) "text/plain" else "application/octet-stream"
}

data class FolderSummary(
    val fileCount: Int,
    val folderCount: Int,
    val extensions: Map<String, Int>,
    val firstFiles: List<String>
)

private fun naturalNameComparator(): Comparator<SafFileEntry> {
    return Comparator { left, right -> naturalCompare(left.name, right.name) }
}

private fun naturalCompare(left: String, right: String): Int {
    var leftIndex = 0
    var rightIndex = 0
    while (leftIndex < left.length && rightIndex < right.length) {
        val leftChar = left[leftIndex]
        val rightChar = right[rightIndex]
        if (leftChar.isDigit() && rightChar.isDigit()) {
            val leftStart = leftIndex
            val rightStart = rightIndex
            while (leftIndex < left.length && left[leftIndex].isDigit()) leftIndex += 1
            while (rightIndex < right.length && right[rightIndex].isDigit()) rightIndex += 1
            val leftNumber = left.substring(leftStart, leftIndex).trimStart('0').ifEmpty { "0" }
            val rightNumber = right.substring(rightStart, rightIndex).trimStart('0').ifEmpty { "0" }
            if (leftNumber.length != rightNumber.length) return leftNumber.length - rightNumber.length
            val numberCompare = leftNumber.compareTo(rightNumber)
            if (numberCompare != 0) return numberCompare
        } else {
            val charCompare = leftChar.lowercaseChar().compareTo(rightChar.lowercaseChar())
            if (charCompare != 0) return charCompare
            leftIndex += 1
            rightIndex += 1
        }
    }
    return left.length - right.length
}
