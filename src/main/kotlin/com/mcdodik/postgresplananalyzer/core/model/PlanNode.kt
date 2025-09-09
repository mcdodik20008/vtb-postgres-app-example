package com.mcdodik.postgresplananalyzer.core.model

data class PlanNode(
    val nodeType: String,
    val relationName: String? = null,
    val schema: String? = null,
    val alias: String? = null,
    val filter: String? = null,
    val indexCond: String? = null,
    val plans: List<PlanNode> = emptyList(),
    val estimatedRows: Double = 0.0,
    val planWidth: Int = 0,
    val totalCost: Double = 0.0,
) {
    fun toPrettyString(
        indent: String = "",
        isLast: Boolean = true,
    ): String {
        val sb = StringBuilder()

        val branch = if (isLast) "└─ " else "├─ "
        sb.append(indent).append(branch)
        sb.append(nodeType)

        relationName?.let { sb.append(" on $it") }
        alias?.let { sb.append(" as $it") }

        sb.append(" (rows=$estimatedRows, cost=$totalCost)")

        if (!filter.isNullOrBlank()) sb.append(" [filter=$filter]")
        if (!indexCond.isNullOrBlank()) sb.append(" [indexCond=$indexCond]")

        sb.appendLine()

        val childIndent = indent + if (isLast) "   " else "│  "
        plans.forEachIndexed { idx, child ->
            sb.append(child.toPrettyString(childIndent, idx == plans.lastIndex))
        }

        return sb.toString()
    }
}
