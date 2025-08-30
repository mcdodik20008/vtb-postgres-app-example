package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class AutovacuumRule(
    private val bigPages: Long = 200_000, // ~ 200k * 8KiB ≈ 1.6 GiB
    private val pageSize: Int = 8192,
) : BaseRule("autovacuum") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val dml =
            query.sql.trimStart().startsWith("UPDATE", true) ||
                query.sql.trimStart().startsWith("DELETE", true) ||
                query.sql.trimStart().startsWith("INSERT", true)
        var hit: Recommendation? = null

        walk(plan.root) { n ->
            if (hit != null) return@walk
            if (n.nodeType == "Seq Scan") {
                val pg = nodePages(n, pageSize)
                if (pg >= bigPages && dml) {
                    val rel = n.relationName ?: "target table"
                    hit =
                        rec(
                            Priority.MEDIUM,
                            "Подозрение на задержку автосборки мусора: $rel",
                            "Крупная таблица (~$pg страниц) участвует в DML. Рассмотри per-table настройки:\n" +
                                "- autovacuum_vacuum_scale_factor ↓ (например 0.05) и autovacuum_vacuum_threshold ↑ при большом объёме\n" +
                                "- autovacuum_analyze_scale_factor ↓ для своевременной пересборки статистики\n" +
                                "- autovacuum_cost_limit/_delay тюнинг под диск\n" +
                                "- регулярный VACUUM/ANALYZE в окно обслуживания",
                            15.0,
                        )
                }
            }
        }
        return listOfNotNull(hit)
    }
}
