package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PlanUtil
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlanNode
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class BloatRule(
    private val minApproxBloatPct: Double = 20.0
) : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        // Без расширений оценим грубо: если в плане встречается Seq Scan с очень большим Plan Width и rows,
        // предлагается проверить блоат и REINDEX/CLUSTER в окно обслуживания.
        val bad = mutableListOf<PlanNode>()
        PlanUtil.walk(plan.root) { n ->
            if (n.nodeType == "Seq Scan" && n.estimatedRows > 5_000_000 && n.planWidth > 200)
                bad += n
        }
        if (bad.isEmpty()) return emptyList()
        return listOf(
            Recommendation(
                priority = Priority.LOW,
                category = "bloat",
                title = "Подозрение на блоат таблицы/индексов",
                detail = "Широкие строки и большие полные сканы. Проверьте bloat, рассмотрите VACUUM (FULL)/REINDEX в окно.",
                expectedGainPct = minApproxBloatPct
            )
        )
    }
}
