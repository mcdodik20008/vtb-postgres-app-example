package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class NPlusOneRule(
    private val outerRowsMin: Long = 1_000,
    private val innerRowsMax: Long = 5,
) : BaseRule("n+1") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType.contains("Nested Loop", true) && n.plans.size >= 2) {
                val outer = n.plans[0]
                val inner = n.plans[1]
                val innerBad =
                    (inner.nodeType == "Seq Scan" || inner.nodeType.contains("Index", true)) &&
                        inner.estimatedRows <= innerRowsMax
                val outerBig = outer.estimatedRows >= outerRowsMin
                if (innerBad && outerBig) {
                    val rel = inner.relationName ?: "inner table"
                    out +=
                        rec(
                            Priority.HIGH,
                            "Подозрение на N+1 (Nested Loop)",
                            "Внешний поток ~${outer.estimatedRows} записей, внутренняя выборка по $rel ~${inner.estimatedRows} на каждую. " +
                                "Советы: объединить в один запрос с JOIN/IN, применить батчирование (IN/VALUES), кэширование.",
                            35.0,
                        )
                }
            }
        }
        return out
    }
}
