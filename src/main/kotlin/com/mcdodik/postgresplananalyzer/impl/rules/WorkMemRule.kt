package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PlanUtil
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class WorkMemRule : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        PlanUtil.walk(plan.root) { n ->
            if (n.nodeType in setOf("Sort", "Hash", "Hash Aggregate")) {
                // грубая оценка: rows * width ~ bytes
                val bytes = (n.estimatedRows * n.planWidth.coerceAtLeast(1)).toLong()
                val needMb = (bytes / (1024 * 1024)).toInt().coerceAtLeast(1)
                if (needMb > settings.workMemMb) {
                    recs += Recommendation(
                        priority = Priority.MEDIUM,
                        category = "config",
                        title = "Недостаточно work_mem для ${n.nodeType}",
                        detail = "Оценка памяти ≈ ${needMb}MB > work_mem=${settings.workMemMb}MB. Возможен spill во временные файлы.",
                        expectedGainPct = 20.0,
                        ddl = "/* В транзакции запроса: */ SET LOCAL work_mem='${needMb}MB';"
                    )
                }
            }
        }
        return recs
    }
}
