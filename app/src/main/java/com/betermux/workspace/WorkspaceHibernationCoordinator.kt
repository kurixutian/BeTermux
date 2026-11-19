package com.betermux.workspace

import android.content.Context
import com.betermux.features.BeTermuxSettings
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates lifecycle-bound hibernation operations (sleep/wake transitions,
 * single-session D2 termination with double-click protection, and cold-start cleanup)
 * across [TermuxActivity] and [TermuxService].
 */
class WorkspaceHibernationCoordinator @JvmOverloads constructor(
    context: Context,
    private val controller: WorkspaceHibernationController = WorkspaceHibernationController.getInstance(context),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    fun interface SessionListProvider {
        fun getSessions(): List<TerminalSession>
    }

    fun interface CompletionCallback {
        fun onComplete()
    }

    private val appContext: Context = context.applicationContext ?: context
    private val terminatingLock = Any()
    private val terminatingHandles = HashSet<String>()

    fun toggleWorkspaceSleep(
        workspaceId: String,
        sessionsProvider: SessionListProvider,
        onComplete: CompletionCallback = CompletionCallback {}
    ): Job? {
        if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) return null
        val currentState = WorkspaceManager.getSleepState(workspaceId)
        if (currentState == WorkspaceSleepState.SUSPENDING || currentState == WorkspaceSleepState.RESUMING) {
            return null
        }

        return scope.launch {
            val sessions = sessionsProvider.getSessions()
            if (currentState == WorkspaceSleepState.SLEEPING || currentState == WorkspaceSleepState.ERROR) {
                controller.wakeWorkspace(workspaceId, sessions)
            } else {
                controller.sleepWorkspace(workspaceId, sessions)
            }
            if (isActive) {
                onComplete.onComplete()
            }
        }
    }

    fun requestWorkspaceWakeIfNeeded(
        workspaceId: String,
        sessionsProvider: SessionListProvider,
        onComplete: CompletionCallback = CompletionCallback {}
    ): Job? {
        if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) return null
        val currentState = WorkspaceManager.getSleepState(workspaceId)
        if (currentState != WorkspaceSleepState.SLEEPING && currentState != WorkspaceSleepState.ERROR) {
            return null
        }

        return scope.launch {
            controller.wakeWorkspace(workspaceId, sessionsProvider.getSessions())
            if (isActive) {
                onComplete.onComplete()
            }
        }
    }

    fun closeSession(
        workspaceId: String,
        session: TerminalSession,
        onComplete: CompletionCallback
    ): Job? {
        val handle = session.mHandle
        synchronized(terminatingLock) {
            if (!terminatingHandles.add(handle)) {
                return null
            }
        }

        return scope.launch {
            try {
                withContext(NonCancellable) {
                    controller.terminateSingleSession(workspaceId, session)
                }
            } finally {
                synchronized(terminatingLock) {
                    terminatingHandles.remove(handle)
                }
            }
            if (isActive) {
                onComplete.onComplete()
            }
        }
    }

    fun isSessionTerminating(handle: String): Boolean {
        synchronized(terminatingLock) {
            return handle in terminatingHandles
        }
    }

    fun destroy() {
        scope.cancel()
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun launchColdStartCleanup(
            context: Context,
            ownerUid: Int,
            controller: WorkspaceHibernationController = WorkspaceHibernationController.getInstance(context),
            scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        ): Job {
            val appContext = context.applicationContext ?: context
            return scope.launch {
                if (BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) {
                    controller.reconcileColdStartLeakedProcesses(ownerUid)
                }
            }
        }
    }
}
