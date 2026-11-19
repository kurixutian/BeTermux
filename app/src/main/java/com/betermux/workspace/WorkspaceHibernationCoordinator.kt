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

    fun interface SessionClosedCallback {
        fun onSessionClosed(session: TerminalSession)
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
            withContext(NonCancellable) {
                if (currentState == WorkspaceSleepState.SLEEPING || currentState == WorkspaceSleepState.ERROR) {
                    controller.wakeWorkspace(workspaceId, sessions)
                } else {
                    controller.sleepWorkspace(workspaceId, sessions)
                }
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
        if (!BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)) {
            onComplete.onComplete()
            return null
        }
        val currentState = WorkspaceManager.getSleepState(workspaceId)
        if (currentState == WorkspaceSleepState.AWAKE) {
            onComplete.onComplete()
            return null
        }

        return scope.launch {
            withContext(NonCancellable) {
                controller.wakeWorkspace(workspaceId, sessionsProvider.getSessions())
            }
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

    /**
     * Sequentially terminates and removes [sessions] in order, invoking [onEachClosed] after each
     * session finishes termination and [onAllClosed] once the full batch has completed.
     * Preserves slot-freeing ordering required by [WorkspaceRemovalPolicy].
     */
    fun closeSessionsSequentially(
        workspaceId: String,
        sessions: List<TerminalSession>,
        onEachClosed: SessionClosedCallback,
        onAllClosed: CompletionCallback
    ): Job? {
        val reserved = ArrayList<TerminalSession>(sessions.size)
        synchronized(terminatingLock) {
            for (session in sessions) {
                if (terminatingHandles.add(session.mHandle)) {
                    reserved.add(session)
                }
            }
        }
        if (reserved.isEmpty()) {
            onAllClosed.onComplete()
            return null
        }

        val hibernationEnabled = BeTermuxSettings.isWorkspaceHibernationEnabled(appContext)
        val needsAsyncTermination = hibernationEnabled && reserved.any { it.pid > 0 }
        if (!needsAsyncTermination) {
            try {
                for (session in reserved) {
                    onEachClosed.onSessionClosed(session)
                }
            } finally {
                synchronized(terminatingLock) {
                    for (session in reserved) {
                        terminatingHandles.remove(session.mHandle)
                    }
                }
            }
            onAllClosed.onComplete()
            return null
        }

        return scope.launch {
            try {
                for (session in reserved) {
                    try {
                        if (session.pid > 0) {
                            withContext(NonCancellable) {
                                controller.terminateSingleSession(workspaceId, session)
                            }
                        }
                    } finally {
                        synchronized(terminatingLock) {
                            terminatingHandles.remove(session.mHandle)
                        }
                    }
                    if (isActive) {
                        onEachClosed.onSessionClosed(session)
                    }
                }
            } finally {
                synchronized(terminatingLock) {
                    for (session in reserved) {
                        terminatingHandles.remove(session.mHandle)
                    }
                }
            }
            if (isActive) {
                onAllClosed.onComplete()
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
