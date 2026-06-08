package com.nani.agent.saf

import android.content.Context
import com.nani.agent.LogStore
import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperationType

class ActionExecutor(
    private val context: Context
) {
    fun execute(plan: ExecutablePlan): ActionExecutionResult {
        val rootUri = SafRootStore.getRootUri(context)
            ?: return ActionExecutionResult(
                successes = emptyList(),
                failures = listOf("No work folder selected."),
                warnings = emptyList()
            )
        val repository = SafFileRepository(context, rootUri)
        val successes = mutableListOf<String>()
        val failures = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        plan.operations.forEach { operation ->
            try {
                val message = when (operation.op) {
                    PlanOperationType.ListFiles -> {
                        val files = repository.listFiles()
                        if (files.isEmpty()) "Work folder is empty." else "Files:\n${files.joinToString("\n")}"
                    }
                    PlanOperationType.SummarizeFolder -> {
                        val summary = repository.summarizeFolder()
                        buildString {
                            appendLine("Files: ${summary.fileCount}")
                            appendLine("Folders: ${summary.folderCount}")
                            appendLine("Extensions: ${summary.extensions.entries.joinToString { "${it.key}=${it.value}" }}")
                            if (summary.firstFiles.isNotEmpty()) {
                                appendLine("First files:")
                                append(summary.firstFiles.joinToString("\n"))
                            }
                        }
                    }
                    PlanOperationType.CreateFolder -> repository.createFolder(requireNotNull(operation.path))
                    PlanOperationType.CopyFile -> repository.copyFile(
                        from = requireNotNull(operation.from),
                        to = requireNotNull(operation.to)
                    )
                    PlanOperationType.Unsupported -> error("Unsupported operation: ${operation.rawOp}")
                }
                successes += message
                LogStore.appendSafAction(context, operation.rawOp, "success")
            } catch (exception: Exception) {
                val message = "${operation.rawOp}: ${exception.message ?: exception::class.java.simpleName}"
                failures += message
                LogStore.appendSafAction(context, operation.rawOp, "failed")
            }
        }

        if (plan.operations.any { it.op == PlanOperationType.CopyFile }) {
            warnings += "Original files were kept. Existing targets were not overwritten."
        }

        return ActionExecutionResult(
            successes = successes,
            failures = failures,
            warnings = warnings
        )
    }
}
