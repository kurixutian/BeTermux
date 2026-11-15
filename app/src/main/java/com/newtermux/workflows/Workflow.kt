package com.newtermux.workflows

/** Placeholder token {@code {{name}}} or {@code {{name=default}}} extracted from a workflow command. */
data class WorkflowToken(
    val name: String,
    val defaultValue: String = ""
)

/** Structured reusable command workflow serialized as YAML-front-matter Markdown. */
data class Workflow(
    val id: String,
    val name: String,
    val description: String = "",
    val tags: List<String> = emptyList(),
    val author: String = "",
    val version: Int = 1,
    val commandTemplate: String,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    fun extractTokens(): List<WorkflowToken> = WorkflowMarkdownParser.extractTokens(commandTemplate)

    fun renderCommand(values: Map<String, String>): String =
        WorkflowMarkdownParser.renderCommand(commandTemplate, values)
}
