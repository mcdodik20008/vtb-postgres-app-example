package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PlanUtil
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlanNode
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class OrderByRule(
    private val minRowsForSort: Double = 50_000.0
) : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        val sorts = mutableListOf<PlanNode>()
        PlanUtil.walk(plan.root) { if (it.nodeType == "Sort" && it.estimatedRows >= minRowsForSort) sorts += it }
        if (sorts.isEmpty()) return emptyList()

        // Эвристика: берём ближайшего потомка Sort с relationName
        val recs = sorts.mapNotNull { s ->
            val base = s.plans.firstOrNull { it.relationName != null } ?: return@mapNotNull null
            // без доступа к Sort Key из JSON тяжело, но часто встречается "Sort Key": "col1, col2"
            val sortKeys = s.filter?.let { Regex("(?i)Sort Key: ([^\\n]+)").find(it)?.groupValues?.get(1) }
            val ddlCols = sortKeys?.split(",")?.map { it.trim() } ?: listOf("/* sort_columns */")
            Recommendation(
                priority = Priority.MEDIUM,
                category = "index",
                title = "Индекс под ORDER BY",
                detail = "Узел Sort на ${"%,.0f".format(s.estimatedRows)} строк. " +
                        "Рассмотрите композитный индекс для снятия внешней сортировки.",
                expectedGainPct = 30.0,
                ddl = "CREATE INDEX CONCURRENTLY idx_${base.relationName}_sort ON ${base.schema ?: "public"}.${base.relationName} (${
                    ddlCols.joinToString(
                        ", "
                    )
                });"
            )
        }
        return recs
    }
}