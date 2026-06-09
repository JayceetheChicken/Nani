package com.nani.agent.agentloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.nani.agent.LogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var currentState = AgentLoopState()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val goal = intent.getStringExtra(EXTRA_GOAL).orEmpty()
                val internetConfirmed = intent.getBooleanExtra(EXTRA_INTERNET_CONFIRMED, false)
                val finalSubmitConfirmed = intent.getBooleanExtra(EXTRA_FINAL_SUBMIT_CONFIRMED, false)
                startAgentForeground()
                startLoop(goal, internetConfirmed, finalSubmitConfirmed)
            }
            ACTION_PAUSE -> pauseLoop()
            ACTION_STOP -> stopLoop()
            ACTION_DISCARD -> discardLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        AgentRuntimeState.update(currentState.copy(running = false), serviceRunning = false)
        super.onDestroy()
    }

    private fun startLoop(goal: String, internetConfirmed: Boolean, finalSubmitConfirmed: Boolean) {
        if (goal.isBlank()) return
        loopJob?.cancel()
        currentState = currentState.takeIf { it.goal == goal && it.stepCount > 0 }
            ?.copy(running = true, paused = false, stopped = false)
            ?: AgentLoopState(goal = goal, running = true)
        AgentRuntimeState.update(currentState, serviceRunning = true)

        loopJob = scope.launch {
            val controller = AgentLoopController(applicationContext)
            while (
                currentState.running &&
                !currentState.paused &&
                !currentState.stopped &&
                currentState.stepCount < currentState.maxSteps
            ) {
                currentState = controller.runNextStep(
                    previous = currentState,
                    goal = goal,
                    internetConfirmed = internetConfirmed,
                    finalSubmitConfirmed = finalSubmitConfirmed
                )
                AgentRuntimeState.update(currentState, serviceRunning = true)

                if (
                    !currentState.running ||
                    currentState.paused ||
                    currentState.stopped ||
                    currentState.stopReason != AgentStopReason.None
                ) {
                    LogStore.appendAgentLoopStep(
                        context = applicationContext,
                        stepNumber = currentState.stepCount,
                        foregroundBefore = "service",
                        action = currentState.lastAction ?: "none",
                        result = currentState.message ?: "stopped",
                        foregroundAfter = "service",
                        stopReason = currentState.stopReason.name
                    )
                    break
                }

                delay(2_500)
            }

            if (currentState.stepCount >= currentState.maxSteps && currentState.running) {
                currentState = currentState.copy(
                    running = false,
                    paused = false,
                    stopped = true,
                    stopReason = AgentStopReason.MaxStepsReached,
                    message = "Max steps reached."
                )
                AgentRuntimeState.update(currentState, serviceRunning = true)
            }
        }
    }

    private fun pauseLoop() {
        loopJob?.cancel()
        currentState = AgentLoopController(applicationContext).pause(currentState)
        AgentRuntimeState.update(currentState, serviceRunning = true)
        LogStore.appendAgentLoopStep(applicationContext, currentState.stepCount, "service", currentState.lastAction ?: "none", "paused", "service", currentState.stopReason.name)
    }

    private fun stopLoop() {
        loopJob?.cancel()
        currentState = AgentLoopController(applicationContext).stop(currentState)
        AgentRuntimeState.update(currentState, serviceRunning = false)
        LogStore.appendAgentLoopStep(applicationContext, currentState.stepCount, "service", currentState.lastAction ?: "none", "stopped", "service", currentState.stopReason.name)
        stopForegroundCompat()
        stopSelf()
    }

    private fun discardLoop() {
        loopJob?.cancel()
        currentState = AgentLoopController(applicationContext).discard()
        AgentRuntimeState.clear()
        stopForegroundCompat()
        stopSelf()
    }

    private fun notification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nani Agent", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Nani")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun startAgentForeground() {
        val notification = notification("Nani agent running")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    companion object {
        const val ACTION_START = "com.nani.agent.agentloop.START"
        const val ACTION_PAUSE = "com.nani.agent.agentloop.PAUSE"
        const val ACTION_STOP = "com.nani.agent.agentloop.STOP"
        const val ACTION_DISCARD = "com.nani.agent.agentloop.DISCARD"
        const val EXTRA_GOAL = "goal"
        const val EXTRA_INTERNET_CONFIRMED = "internetConfirmed"
        const val EXTRA_FINAL_SUBMIT_CONFIRMED = "finalSubmitConfirmed"
        private const val CHANNEL_ID = "nani_agent_runtime"
        private const val NOTIFICATION_ID = 2401
    }
}
