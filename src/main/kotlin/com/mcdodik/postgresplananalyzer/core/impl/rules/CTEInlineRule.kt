package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class CTEInlineRule(
    private val bigRows: Long = 100_000,
) : BaseRule("cte-inline") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            val t = n.nodeType.lowercase()
            if ((t.contains("cte scan") || t.contains("materialize")) && n.estimatedRows > bigRows) {
                out +=
                    rec(
                        Priority.MEDIUM,
                        "Крупный CTE/Materialize (~${n.estimatedRows} строк)",
                        "В 12+ можно NOT MATERIALIZED (если безопасно) или убрать CTE, чтобы дать оптимизатору inline.",
                        15.0,
                    )
            }
        }
        return out
    }
}
