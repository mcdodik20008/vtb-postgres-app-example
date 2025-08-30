package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class BloatRule(
    private val minRows: Long = 5_000_000,
    private val minWidth: Int = 200,
) : BaseRule("bloat") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        var hit = false
        walk(plan.root) { n ->
            if (n.nodeType == "Seq Scan" && n.estimatedRows >= minRows && n.planWidth >= minWidth) hit = true
        }
        return if (!hit) {
            emptyList()
        } else {
            listOf(
                rec(
                    Priority.LOW,
                    "Подозрение на блоат таблицы/индексов",
                    "Широкие строки и большие полные сканы. Проверь bloat; планируй VACUUM(FULL)/REINDEX в окно обслуживания.",
                    20.0,
                ),
            )
        }
    }
}
