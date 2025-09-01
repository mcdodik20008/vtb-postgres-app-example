package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class ParallelDisabledRule(
    private val minPlanBytes: Long = 256L shl 20,
) : BaseRule("parallel") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        var total = 0L
        var hasGather = false
        walk(plan.root) { n ->
            total += nodeBytes(n)
            if (n.nodeType.contains("Gather", true)) hasGather = true
        }
        return if (total >= minPlanBytes && !hasGather) {
            listOf(
                rec(
                    Priority.MEDIUM,
                    "Параллелизм не используется при объёме ~${humanBytes(total)}",
                    "Проверь допуск запроса к parallel и параметры кластера (max_parallel_workers_per_gather и т. п.).",
                    20.0,
                ),
            )
        } else {
            emptyList()
        }
    }
}
