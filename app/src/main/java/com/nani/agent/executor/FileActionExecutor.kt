package com.nani.agent.executor

import android.content.Context
import com.nani.agent.LogStore
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperationType
import com.nani.agent.saf.ActionExecutionResult
import com.nani.agent.saf.BroadFileRepository
import com.nani.agent.saf.BroadStorageAccess
import com.nani.agent.saf.FolderSummary
import com.nani.agent.saf.SafFileRepository
import com.nani.agent.saf.SafRootStore

class FileActionExecutor(
    private val context: Context
) {
    fun execute(plan: ExecutablePlan): ActionExecutionResult {
        val safRoot = SafRootStore.getRootUri(context)
        val safRepository = safRoot?.let { SafFileRepository(context, it) }
        val broadRepository = if (safRepository == null && BroadStorageAccess.isGranted()) {
            BroadFileRepository()
        } else {
            null
        }

        if (safRepository == null && broadRepository == null) {
            return ActionExecutionResult(
                successes = emptyList(),
                failures = listOf("No SAF work folder selected and Broad Agent Storage is not granted."),
                warnings = emptyList()
            )
        }

        val validation = com.nani.agent.plan.PlanValidator.validate(
            plan = plan,
            hasWorkFolder = true,
            internetConfirmed = false
        )
        if (!validation.isValid) {
            return ActionExecutionResult(
                successes = emptyList(),
                failures = validation.errors.ifEmpty { listOf("File executor refused this plan.") },
                warnings = validation.warnings
            )
        }

        val successes = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        plan.operations.forEach { operation ->
            try {
                val message = when (operation.op) {
                    PlanOperationType.ListFiles -> safRepository?.listFiles()?.formatList("Files")
                        ?: broadRepository!!.listFiles().formatList("Files")
                    PlanOperationType.ReadFile -> safRepository?.readFile(requireNotNull(operation.path))
                        ?: broadRepository!!.readFile(requireNotNull(operation.path))
                    PlanOperationType.SummarizeFile -> safRepository?.summarizeFile(requireNotNull(operation.path))
                        ?: broadRepository!!.summarizeFile(requireNotNull(operation.path))
                    PlanOperationType.SummarizeFolder -> safRepository?.summarizeFolder()?.format()
                        ?: broadRepository!!.summarizeFolder().format()
                    PlanOperationType.SearchFiles -> safRepository?.searchFiles(operation.query.orEmpty())?.formatList("Matches")
                        ?: broadRepository!!.searchFiles(operation.query.orEmpty()).formatList("Matches")
                    PlanOperationType.ClassifyFiles -> safRepository?.classifyFiles()?.formatList("Classifications")
                        ?: broadRepository!!.classifyFiles().formatList("Classifications")
                    PlanOperationType.CreateFolder -> safRepository?.createFolder(requireNotNull(operation.path))
                        ?: broadRepository!!.createFolder(requireNotNull(operation.path))
                    PlanOperationType.CreateFile -> safRepository?.createFile(requireNotNull(operation.path), operation.content.orEmpty())
                        ?: broadRepository!!.createFile(requireNotNull(operation.path), operation.content.orEmpty())
                    PlanOperationType.EditTextFile -> safRepository?.editTextFile(requireNotNull(operation.path), operation.content.orEmpty())
                        ?: broadRepository!!.editTextFile(requireNotNull(operation.path), operation.content.orEmpty())
                    PlanOperationType.AppendTextFile -> safRepository?.appendTextFile(requireNotNull(operation.path), operation.content.orEmpty())
                        ?: broadRepository!!.appendTextFile(requireNotNull(operation.path), operation.content.orEmpty())
                    PlanOperationType.CopyFile -> safRepository?.copyFile(requireNotNull(operation.from), requireNotNull(operation.to))
                        ?: broadRepository!!.copyFile(requireNotNull(operation.from), requireNotNull(operation.to))
                    PlanOperationType.RenameFile -> safRepository?.renameFile(requireNotNull(operation.from), requireNotNull(operation.to))
                        ?: broadRepository!!.renameFile(requireNotNull(operation.from), requireNotNull(operation.to))
                    PlanOperationType.OpenUrl,
                    PlanOperationType.UseApp,
                    PlanOperationType.Unsupported -> error("Operation is not a file operation in this build: ${operation.rawOp}")
                    else -> error("Operation is not a file operation: ${operation.rawOp}")
                }
                successes += message
                LogStore.appendSafAction(context, operation.rawOp, "success")
            } catch (exception: Exception) {
                failures += "${operation.rawOp}: ${exception.message ?: exception::class.java.simpleName}"
                LogStore.appendSafAction(context, operation.rawOp, "failed")
            }
        }

        if (plan.operations.any { it.op == PlanOperationType.CopyFile }) {
            warnings += "Original files were kept. Existing targets were not overwritten."
        }

        return ActionExecutionResult(successes, failures, warnings)
    }
}

private fun List<String>.formatList(title: String): String {
    return if (isEmpty()) "$title: none" else "$title:\n${joinToString("\n")}"
}

private fun FolderSummary.format(): String {
    return buildString {
        appendLine("Files: $fileCount")
        appendLine("Folders: $folderCount")
        appendLine("Extensions: ${extensions.entries.joinToString { "${it.key}=${it.value}" }}")
        if (firstFiles.isNotEmpty()) {
            appendLine("First files:")
            append(firstFiles.joinToString("\n"))
        }
    }
}
