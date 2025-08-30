package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class SeqScanLargeTableRule(
    private val minPages: Long = 1000,
    private val pageSize: Int = 8192,
) : BaseRule("seq-scan") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType == "Seq Scan") {
                val pg = nodePages(n, pageSize)
                val hasSel = equalityColumns(n.filter).isNotEmpty() || rangeColumns(n.filter).isNotEmpty()
                if (pg >= minPages && !hasSel) {
                    val rel = n.relationName ?: "target table"
                    out +=
                        rec(
                            Priority.HIGH,
                            "Крупный Seq Scan без селективного фильтра: $rel",
                            "Оценка сканирования ~$pg страниц. Добавь индекс (возможно partial), перепиши WHERE, подумай о секционировании.",
                            50.0,
                        )
                }
            }
        }
        return out
    }
}
