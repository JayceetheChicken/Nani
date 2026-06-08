package com.nani.agent.plan

import com.nani.agent.ai.AiAction
import com.nani.agent.ai.AiRiskLevel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanValidatorTest {
    @Test
    fun blocksDeleteMoveAndWipeOperations() {
        listOf("delete_file", "move_file", "wipe_folder").forEach { rawOp ->
            val plan = basePlan(
                operations = listOf(
                    PlanOperation(
                        op = PlanOperationType.Unsupported,
                        rawOp = rawOp
                    )
                )
            )

            val result = PlanValidator.validate(plan, hasWorkFolder = true)

            assertFalse(result.isValid)
            assertTrue(result.errors.any { it.contains("forbidden") })
        }
    }

    @Test
    fun blocksAbsolutePathsAndParentTraversal() {
        val absolutePlan = basePlan(
            operations = listOf(
                PlanOperation(
                    op = PlanOperationType.CreateFolder,
                    rawOp = "create_folder",
                    path = "C:/Users/nilsv/secrets"
                )
            )
        )
        val traversalPlan = basePlan(
            operations = listOf(
                PlanOperation(
                    op = PlanOperationType.CopyFile,
                    rawOp = "copy_file",
                    from = "../secret.pdf",
                    to = "Schule/secret.pdf"
                )
            )
        )

        assertFalse(PlanValidator.validate(absolutePlan, hasWorkFolder = true).isValid)
        assertFalse(PlanValidator.validate(traversalPlan, hasWorkFolder = true).isValid)
    }

    @Test
    fun blocksClarifyingQuestionEvenWithOperations() {
        val plan = basePlan(
            actionType = AiAction.AskClarifyingQuestion,
            requiresConfirmation = true,
            operations = listOf(
                PlanOperation(
                    op = PlanOperationType.CreateFolder,
                    rawOp = "create_folder",
                    path = "Schule"
                )
            )
        )

        val result = PlanValidator.validate(plan, hasWorkFolder = true)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Clarifying-question") })
    }

    @Test
    fun allowsCreateFolderAndCopyFileOnlyWithWorkFolderAndConfirmation() {
        val plan = basePlan(
            requiresConfirmation = true,
            operations = listOf(
                PlanOperation(
                    op = PlanOperationType.CreateFolder,
                    rawOp = "create_folder",
                    path = "Schule/Mathe"
                ),
                PlanOperation(
                    op = PlanOperationType.CopyFile,
                    rawOp = "copy_file",
                    from = "Downloads/ableitungen.pdf",
                    to = "Schule/Mathe/ableitungen.pdf"
                )
            )
        )

        assertTrue(PlanValidator.validate(plan, hasWorkFolder = true).isValid)
        assertFalse(PlanValidator.validate(plan.copy(requiresConfirmation = false), hasWorkFolder = true).isValid)
        assertFalse(PlanValidator.validate(plan, hasWorkFolder = false).isValid)
    }

    @Test
    fun allowsRenameWithWorkFolderAndConfirmation() {
        val plan = basePlan(
            requiresConfirmation = true,
            operations = listOf(
                PlanOperation(
                    op = PlanOperationType.RenameFile,
                    rawOp = "rename_file",
                    from = "Schule/alt.txt",
                    to = "Schule/neu.txt"
                )
            )
        )

        assertTrue(PlanValidator.validate(plan, hasWorkFolder = true).isValid)
    }

    @Test
    fun blocksInternetOperationsUntilSeparatelyConfirmed() {
        val plan = basePlan(
            actionType = AiAction.UseBrowser,
            requiresConfirmation = true,
            requiresInternetConfirmation = true,
            operations = listOf(
                PlanOperation(
                    op = PlanOperationType.OpenUrl,
                    rawOp = "open_url",
                    url = "https://example.com",
                    reason = "Research"
                )
            )
        )

        assertFalse(PlanValidator.validate(plan, hasWorkFolder = true, internetConfirmed = false).isValid)
        assertTrue(PlanValidator.validate(plan, hasWorkFolder = true, internetConfirmed = true).isValid)
    }

    private fun basePlan(
        actionType: AiAction = AiAction.OrganizeFiles,
        requiresConfirmation: Boolean = true,
        requiresInternetConfirmation: Boolean = false,
        riskLevel: AiRiskLevel = AiRiskLevel.Medium,
        operations: List<PlanOperation>
    ): ExecutablePlan {
        return ExecutablePlan(
            actionType = actionType,
            explanation = "Test plan",
            requiresConfirmation = requiresConfirmation,
            requiresInternetConfirmation = requiresInternetConfirmation,
            riskLevel = riskLevel,
            operations = operations,
            proposedJson = "{}"
        )
    }
}
