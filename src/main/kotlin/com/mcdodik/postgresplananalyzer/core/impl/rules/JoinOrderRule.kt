package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class JoinOrderRule(
    private val bigRows: Long = 100_000,
) : BaseRule("join-order") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            val t = n.nodeType
            if (t.contains("Join", true) && n.plans.size >= 2) {
                val left = n.plans[0]
                val right = n.plans[1]
                if (t.contains("Nested Loop", true) && right.estimatedRows > bigRows && right.nodeType == "Seq Scan") {
                    recs +=
                        rec(
                            Priority.HIGH,
                            "Nested Loop с большой внутренней таблицей",
                            "Внутри ~${right.estimatedRows} строк без индекса. Индекс по join-keys, поменять порядок, рассмотреть Hash/Merge.",
                            40.0,
                        )
                } else if (t.contains("Hash", true) && left.estimatedRows > bigRows && right.estimatedRows > bigRows) {
                    recs +=
                        rec(
                            Priority.MEDIUM,
                            "Hash Join на двух крупных входах",
                            "Проверь кардинальности и составные индексы под ключи соединения; возможно reorder join.",
                            25.0,
                        )
                }
            }
        }
        return recs
    }
}
