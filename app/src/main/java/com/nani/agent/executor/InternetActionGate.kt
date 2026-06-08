package com.nani.agent.executor

import com.nani.agent.plan.ExecutablePlan

object InternetActionGate {
    fun canProceed(plan: ExecutablePlan, internetConfirmed: Boolean): Boolean {
        return !plan.requiresInternetConfirmation || internetConfirmed
    }
}
