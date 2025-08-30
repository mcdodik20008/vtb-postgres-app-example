package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PlanUtil
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlanNode
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class PartitioningRule(
    private val minRowsForPartition: Double = 10_000_000.0
) : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        // Ищем диапазонные фильтры по колонкам "created_at","event_time","ts"
        val dateCols = listOf("created_at", "event_time", "ts", "timestamp", "date")
        val rngNode = mutableListOf<PlanNode>()
        PlanUtil.walk(plan.root) { n ->
            if (n.nodeType == "Seq Scan" && dateCols.any { n.filter?.contains(it) == true } && n.estimatedRows >= minRowsForPartition)
                rngNode += n
        }
        return rngNode.take(1).map { n ->
            Recommendation(
                priority = Priority.MEDIUM,
                category = "partition",
                title = "Рассмотрите секционирование по дате",
                detail = "Большой диапазонный скан по дате в ${n.relationName}. Секционирование RANGE по дню/месяцу сузит объём сканирования.",
                expectedGainPct = 30.0,
                ddl = """
                    -- пример (упрощён): секционирование по месяцам
                    -- CREATE TABLE ${n.schema ?: "public"}.${n.relationName}_p (
                    --   LIKE ${n.schema ?: "public"}.${n.relationName} INCLUDING ALL
                    -- ) PARTITION BY RANGE (created_at);
                    -- ALTER TABLE ... ATTACH PARTITION ... FOR VALUES FROM (...) TO (...);
                """.trimIndent()
            )
        }
    }
}