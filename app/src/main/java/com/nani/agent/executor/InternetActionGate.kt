package com.nani.agent.executor

import com.nani.agent.plan.ExecutablePlan
import com.nani.agent.plan.PlanOperation
import com.nani.agent.policy.NetworkPolicy

object InternetActionGate {
    fun inspect(plan: ExecutablePlan): InternetActionRequest {
        val internetOperations = plan.operations.filter {
            plan.requiresInternetConfirmation || it.op.usesInternet || NetworkPolicy.operationRequiresInternetConfirmation(it.rawOp)
        }
        return InternetActionRequest(
            usesBrowser = internetOperations.any { it.rawOp.contains("browser", ignoreCase = true) || it.rawOp == "open_url" },
            opensUrl = internetOperations.any { it.url != null || it.rawOp == "open_url" },
            uploadsFile = internetOperations.any { it.rawOp.contains("upload", ignoreCase = true) },
            sendsData = internetOperations.any { it.rawOp.contains("send", ignoreCase = true) || it.rawOp.contains("submit", ignoreCase = true) },
            usesWebSearch = internetOperations.any { it.rawOp.contains("search", ignoreCase = true) },
            usesCloud = internetOperations.any { it.rawOp.contains("cloud", ignoreCase = true) || it.rawOp.contains("share", ignoreCase = true) },
            operations = internetOperations
        )
    }

    fun canProceed(plan: ExecutablePlan, internetConfirmed: Boolean): Boolean {
        return !inspect(plan).requiresConfirmation || internetConfirmed
    }
}

data class InternetActionRequest(
    val usesBrowser: Boolean,
    val opensUrl: Boolean,
    val uploadsFile: Boolean,
    val sendsData: Boolean,
    val usesWebSearch: Boolean,
    val usesCloud: Boolean,
    val operations: List<PlanOperation>
) {
    val requiresConfirmation: Boolean
        get() = usesBrowser || opensUrl || uploadsFile || sendsData || usesWebSearch || usesCloud || operations.isNotEmpty()
}

data class InternetConfirmationState(
    val confirmed: Boolean = false,
    val confirmedAtMillis: Long? = null
)
