package com.newtermux.input

import android.content.Context
import android.content.SharedPreferences
import com.newtermux.features.TextExpansionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Durable per-session command draft store.
 *
 * Persists drafts both by runtime session handle and by tab slot index in
 * {@code newtermux_input_drafts} so drafts survive FloatingEditLayer dismissal,
 * configuration changes, and OS process death.
 */
class InputDraftStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts: StateFlow<Map<String, String>> = _drafts.asStateFlow()

    private val handleToSlot = LinkedHashMap<String, Int>()
    private val slotToHandle = LinkedHashMap<Int, String>()

    init {
        val loaded = LinkedHashMap<String, String>()
        for ((key, value) in prefs.all) {
            if (value is String && key.startsWith(KEY_HANDLE_PREFIX)) {
                val handle = key.removePrefix(KEY_HANDLE_PREFIX)
                if (handle.isNotEmpty() && value.isNotEmpty()) {
                    loaded[handle] = value
                }
            }
        }
        _drafts.value = loaded
    }

    /**
     * Synchronize ordered session handles with tab indices after startup or tab mutations.
     * Restores persisted slot drafts onto newly assigned handles after process death.
     */
    @Synchronized
    fun syncSessionHandles(orderedHandles: List<String>) {
        handleToSlot.clear()
        slotToHandle.clear()
        val editor = prefs.edit()
        var dirtyPrefs = false
        val nextMap = LinkedHashMap(_drafts.value)

        orderedHandles.forEachIndexed { index, handle ->
            if (handle.isEmpty()) return@forEachIndexed
            handleToSlot[handle] = index
            slotToHandle[index] = handle

            val currentInMemory = nextMap[handle]
            val slotKey = KEY_SLOT_PREFIX + index
            if (currentInMemory.isNullOrEmpty()) {
                val restoredFromHandle = prefs.getString(KEY_HANDLE_PREFIX + handle, null)
                val restored = if (!restoredFromHandle.isNullOrEmpty()) {
                    restoredFromHandle
                } else {
                    prefs.getString(slotKey, null)
                }
                if (!restored.isNullOrEmpty()) {
                    nextMap[handle] = restored
                    editor.putString(KEY_HANDLE_PREFIX + handle, restored)
                    dirtyPrefs = true
                }
            } else {
                editor.putString(slotKey, currentInMemory)
                dirtyPrefs = true
            }
        }

        if (dirtyPrefs) {
            editor.apply()
        }
        _drafts.value = nextMap
    }

    @Synchronized
    fun getDraft(sessionHandle: String): String {
        if (sessionHandle.isEmpty()) return ""
        return _drafts.value[sessionHandle] ?: prefs.getString(KEY_HANDLE_PREFIX + sessionHandle, "") ?: ""
    }

    @Synchronized
    fun hasNonBlankDraft(sessionHandle: String): Boolean = getDraft(sessionHandle).isNotBlank()

    @Synchronized
    fun setDraft(sessionHandle: String, text: String, commitSync: Boolean = false) {
        if (sessionHandle.isEmpty()) return
        val normalized = text.replace("\r\n", "\n")
        _drafts.update { current ->
            val next = LinkedHashMap(current)
            if (normalized.isEmpty()) next.remove(sessionHandle) else next[sessionHandle] = normalized
            next
        }
        val editor = prefs.edit()
        val slot = handleToSlot[sessionHandle]
        if (normalized.isEmpty()) {
            editor.remove(KEY_HANDLE_PREFIX + sessionHandle)
            if (slot != null) editor.remove(KEY_SLOT_PREFIX + slot)
        } else {
            editor.putString(KEY_HANDLE_PREFIX + sessionHandle, normalized)
            if (slot != null) editor.putString(KEY_SLOT_PREFIX + slot, normalized)
        }
        if (commitSync) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    @Synchronized
    fun appendText(sessionHandle: String, snippet: String) {
        if (sessionHandle.isEmpty() || snippet.isEmpty()) return
        val existing = getDraft(sessionHandle)
        val joined = when {
            existing.isEmpty() -> snippet
            existing.endsWith("\n") || existing.endsWith(" ") -> existing + snippet
            else -> "$existing $snippet"
        }
        setDraft(sessionHandle, joined)
    }

    @Synchronized
    fun clearDraft(sessionHandle: String, commitSync: Boolean = false) {
        setDraft(sessionHandle, "", commitSync = commitSync)
    }

    @Synchronized
    fun removeSessionDraft(sessionHandle: String, commitSync: Boolean = true) {
        if (sessionHandle.isEmpty()) return
        val slot = handleToSlot.remove(sessionHandle)
        if (slot != null) slotToHandle.remove(slot)
        _drafts.update { current ->
            val next = LinkedHashMap(current)
            next.remove(sessionHandle)
            next
        }
        val editor = prefs.edit().remove(KEY_HANDLE_PREFIX + sessionHandle)
        if (slot != null) editor.remove(KEY_SLOT_PREFIX + slot)
        if (commitSync) editor.commit() else editor.apply()
    }

    @Synchronized
    fun flushAllSync() {
        val editor = prefs.edit()
        for ((handle, text) in _drafts.value) {
            if (handle.isNotEmpty() && text.isNotEmpty()) {
                editor.putString(KEY_HANDLE_PREFIX + handle, text)
                handleToSlot[handle]?.let { slot -> editor.putString(KEY_SLOT_PREFIX + slot, text) }
            }
        }
        editor.commit()
    }

    companion object {
        const val PREFS_NAME = "newtermux_input_drafts"
        private const val KEY_HANDLE_PREFIX = "handle_"
        private const val KEY_SLOT_PREFIX = "slot_"

        /**
         * Save an unsent draft into {@link TextExpansionStore} with a deterministic unique trigger.
         * Returns the stored trigger name, or null if draft was blank.
         */
        @JvmStatic
        fun saveDraftAsSnippet(context: Context, draft: String): String? {
            val cleaned = draft.trim()
            if (cleaned.isEmpty()) return null
            val firstLine = cleaned.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: cleaned
            val stem = firstLine
                .replace(Regex("[^A-Za-z0-9_.-]+"), "_")
                .trim('_')
                .take(18)
                .ifEmpty { "draft" }

            val existing = ArrayList(TextExpansionStore.load(context))
            val used = existing.mapTo(HashSet()) { it.trigger }
            var candidate = "/$stem"
            var suffix = 2
            while (candidate in used) {
                candidate = "/${stem}_$suffix"
                suffix++
            }
            val item = TextExpansionStore.TextExpansion().apply {
                trigger = candidate
                expansion = cleaned
            }
            existing.add(item)
            TextExpansionStore.save(context, existing)
            return candidate
        }
    }
}
