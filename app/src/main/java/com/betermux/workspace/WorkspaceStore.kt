package com.betermux.workspace

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON persistence for workspace identities, ordering, and session handle associations.
 * Intentionally excludes runtime [WorkspaceSleepState] (D4).
 */
internal object WorkspaceStore {
    private const val PREFS_NAME = "betermux_workspaces"
    private const val KEY_WORKSPACES_JSON = "workspaces_json"
    private const val KEY_ACTIVE_WORKSPACE_ID = "active_workspace_id"

    internal data class PersistedState(
        val workspaces: List<ProjectWorkspace>,
        val activeWorkspaceId: String?
    )

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun load(context: Context): PersistedState {
        val sp = prefs(context)
        val rawJson = sp.getString(KEY_WORKSPACES_JSON, null)
        val activeId = sp.getString(KEY_ACTIVE_WORKSPACE_ID, null)?.trim()?.takeIf { it.isNotEmpty() }

        if (rawJson.isNullOrBlank()) {
            return PersistedState(emptyList(), activeId)
        }

        val parsed = ArrayList<ProjectWorkspace>()
        val seenIds = HashSet<String>()
        val seenHandles = HashSet<String>()

        try {
            val array = JSONArray(rawJson)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.isNull("id") || obj.isNull("rootPath")) continue
                val id = obj.optString("id", "").trim()
                val rootPath = obj.optString("rootPath", "").trim()
                if (id.isEmpty() || rootPath.isEmpty()) continue
                if (!seenIds.add(id)) continue

                val rawName = if (obj.isNull("name")) "" else obj.optString("name", "").trim()
                val name = when {
                    rawName.isNotEmpty() -> rawName
                    id == DEFAULT_WORKSPACE_ID -> DEFAULT_WORKSPACE_NAME
                    else -> rootPath.substringAfterLast('/').ifEmpty { id }
                }
                val sortOrder = obj.optInt("sortOrder", i)
                val handles = parseSessionHandles(obj.optJSONArray("sessionHandles"), seenHandles)

                // Note: any legacy/unknown fields such as "isSleeping" are intentionally ignored (D4).
                parsed.add(
                    ProjectWorkspace(
                        id = id,
                        name = name,
                        rootPath = rootPath,
                        sortOrder = sortOrder,
                        sessionHandles = handles
                    )
                )
            }
        } catch (_: Exception) {
            return PersistedState(emptyList(), activeId)
        }

        return PersistedState(parsed, activeId)
    }

    fun save(context: Context, workspaces: List<ProjectWorkspace>, activeWorkspaceId: String) {
        val array = JSONArray()
        for (ws in workspaces) {
            val obj = JSONObject()
            obj.put("id", ws.id)
            obj.put("name", ws.name)
            obj.put("rootPath", ws.rootPath)
            obj.put("sortOrder", ws.sortOrder)
            val handlesArray = JSONArray()
            for (handle in ws.sessionHandles) {
                handlesArray.put(handle)
            }
            obj.put("sessionHandles", handlesArray)
            array.put(obj)
        }
        prefs(context).edit()
            .putString(KEY_WORKSPACES_JSON, array.toString())
            .putString(KEY_ACTIVE_WORKSPACE_ID, activeWorkspaceId)
            .apply()
    }

    private fun parseSessionHandles(array: JSONArray?, seenHandles: MutableSet<String>): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        val handles = ArrayList<String>(array.length())
        for (j in 0 until array.length()) {
            if (array.isNull(j)) continue
            val handle = array.optString(j, "").trim()
            if (handle.isNotEmpty() && seenHandles.add(handle)) {
                handles.add(handle)
            }
        }
        return handles
    }
}
