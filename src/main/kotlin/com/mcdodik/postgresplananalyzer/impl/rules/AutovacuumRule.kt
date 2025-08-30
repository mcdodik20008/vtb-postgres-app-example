package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PlanUtil
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlanNode
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class AutovacuumRule(
    private val minDeadTuplesRatio: Double = 0.2   // 20% мёртвых оценочно
) : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        // Эвристика без расширений: смотрим большие Seq Scan узлы и даём общий совет
        val bigSeq = mutableListOf<PlanNode>()
        PlanUtil.walk(plan.root) { if (it.nodeType == "Seq Scan" && it.estimatedRows > 1_000_000) bigSeq += it }
        if (bigSeq.isEmpty()) return emptyList()
        return listOf(
            Recommendation(
                priority = Priority.MEDIUM,
                category = "vacuum",
                title = "Проверьте autovacuum/аналитику",
                detail = "На больших таблицах замечены Seq Scan. Проверьте частоту autovacuum, dead tuples и autovacuum_vacuum_scale_factor. Возможна настройка на уровне таблицы.",
                expectedGainPct = 15.0,
                ddl = "ALTER TABLE ${bigSeq.first().schema ?: "public"}.${bigSeq.first().relationName} SET (autovacuum_vacuum_scale_factor = 0.05, autovacuum_analyze_scale_factor = 0.05);"
            )
        )
    }
}
