package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class BitmapRecheckRule(
    private val minRows: Long = 50_000,
) : BaseRule("bitmap-recheck") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType == "Bitmap Heap Scan" && n.estimatedRows >= minRows) {
                val rel = n.relationName ?: "target table"
                out +=
                    rec(
                        Priority.MEDIUM,
                        "Bitmap Heap Scan на большом наборе: $rel",
                        "Высокая вероятность recheck. Рассмотри другой тип/частичный индекс и уточнение предикатов.",
                        12.0,
                    )
            }
        }
        return out
    }
}
