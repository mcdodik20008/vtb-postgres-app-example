package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class SortSpillRule : BaseRule("sort-spill") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val wm = workMemBytes(settings)
        val recs = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType == "Sort") {
                val need = nodeBytes(n) * 2L
                if (need > wm) {
                    val keysList = sortKeysFromSql(query.sql)
                    val keys = if (keysList.isEmpty()) "<unknown>" else keysList.joinToString(", ")
                    recs +=
                        rec(
                            Priority.MEDIUM,
                            "Sort spill: требуется ~${humanBytes(need)}, work_mem ≈ ${humanBytes(wm)}",
                            "Ключи: $keys. Подними work_mem для запроса, добавь индекс под ORDER BY, сделай limit pushdown.",
                            20.0,
                        )
                }
            }
        }
        return recs
    }
}
