package com.mcdodik.postgresplananalyzer.core.model

data class Plan(
    val root: PlanNode,
    val totalCost: Double,
) {
    override fun toString(): String =
        buildString {
            appendLine("Plan(totalCost=$totalCost)")
            append(root.toPrettyString())
        }
}
