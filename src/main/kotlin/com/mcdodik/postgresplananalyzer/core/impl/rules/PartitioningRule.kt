package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class PartitioningRule(
    private val hugePages: Long = 500_000, // ~ 3.8 GiB
    private val pageSize: Int = 8192,
) : BaseRule("partitioning") {
    private val dateLike = Regex("""(?i).*(created|date|dt|time|timestamp).*""")

    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType == "Seq Scan") {
                val pg = nodePages(n, pageSize)
                val rng = rangeColumns(n.filter)
                val looksDate = rng.any { dateLike.matches(it) }
                if (pg >= hugePages && (rng.isNotEmpty() || looksDate)) {
                    val rel = n.relationName ?: "target table"
                    out +=
                        rec(
                            Priority.MEDIUM,
                            "Таблица $rel выглядит кандидатом на секционирование",
                            "Полный скан ~$pg страниц с диапазонным фильтром (${rng.joinToString().ifEmpty { "range" }}). " +
                                "Рассмотри RANGE-секционирование по дате/идентификатору, ключевые индексы на детях, " +
                                "и правильные CHECK/CONSTRAINT EXCLUSION.",
                            22.0,
                        )
                }
            }
        }
        return out
    }
}
