package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class SkewStatsRule(
    private val minRows: Long = 100_000,
    private val minCols: Int = 2,
) : BaseRule("skew-stats") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            val cols = (equalityColumns(n.filter) + rangeColumns(n.filter)).distinct()
            if (cols.size >= minCols && n.estimatedRows >= minRows) {
                val rel = n.relationName ?: "target table"
                out +=
                    rec(
                        Priority.MEDIUM,
                        "Вероятный перекос селективности на $rel",
                        "Многофакторный предикат по ${cols.joinToString()} даёт большой остаток. " +
                            "Создай extended statistics (ndistinct/mcv/dependencies) и partial/expression index под реальный предикат.",
                        10.0,
                    )
            }
        }
        return out
    }
}
