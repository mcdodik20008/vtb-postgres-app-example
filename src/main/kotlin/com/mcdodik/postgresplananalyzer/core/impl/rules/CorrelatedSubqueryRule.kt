package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class CorrelatedSubqueryRule(
    private val bigRowsInner: Long = 10_000,
) : BaseRule("correlated-subquery") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType.contains("Nested Loop", true) && n.plans.size >= 2) {
                val inner = n.plans[1]
                val looksBad =
                    inner.estimatedRows > bigRowsInner &&
                        inner.nodeType == "Seq Scan" &&
                        equalityColumns(inner.filter).isEmpty()
                if (looksBad) {
                    out +=
                        rec(
                            Priority.HIGH,
                            "Возможен коррелированный подзапрос",
                            "Внутренняя часть ~${inner.estimatedRows} строк без индекса/селективности. Перепиши в JOIN/CTE или материализуй с индексом.",
                            45.0,
                        )
                }
            }
        }
        return out
    }
}
