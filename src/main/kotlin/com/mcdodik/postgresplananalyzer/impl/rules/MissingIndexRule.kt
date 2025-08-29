package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlanNode
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class MissingIndexRule(
    private val minPages: Long = 1000, // порог размера таблицы (страниц)
    private val minGainPct: Double = 0.2 // минимум экономии для рекомендации
) : Rule {

    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()

        fun visit(node: PlanNode) {
            if (node.nodeType == "Seq Scan" && node.relationName != null) {
                // Допущение: relpages из Estimates.ioPages можно использовать как прокси размера
                val tablePages = (node.estimatedRows * node.planWidth / 8192).toLong().coerceAtLeast(1)

                if (tablePages >= minPages && !node.filter.isNullOrBlank()) {
                    val gain = 0.5 // тут можно делать оценку точнее, пока фиксированное 50%

                    if (gain >= minGainPct) {
                        val idxName = "idx_${node.relationName}_${System.currentTimeMillis() % 10000}"
                        val ddl =
                            "CREATE INDEX $idxName ON ${node.schema ?: "public"}.${node.relationName} (/* columns from filter */);"

                        recs += Recommendation(
                            priority = if (gain > 0.5) Priority.HIGH else Priority.MEDIUM,
                            category = "index",
                            title = "Рассмотрите добавление индекса",
                            detail = "Seq Scan на таблице ${node.relationName} (≈$tablePages страниц). " +
                                    "Фильтр: ${node.filter}. Индекс может ускорить выполнение.",
                            expectedGainPct = gain * 100,
                            ddl = ddl
                        )
                    }
                }
            }
            node.plans.forEach(::visit)
        }

        visit(plan.root)
        return recs
    }
}