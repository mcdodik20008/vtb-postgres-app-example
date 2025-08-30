package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class HashSpillRule : BaseRule("hash-spill") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val wm = workMemBytes(settings)
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            val t = n.nodeType.lowercase()
            if (t.contains("hash join") || t == "hash" || t.contains("hashagg")) {
                val need = (nodeBytes(n) * 1.5).toLong()
                if (need > wm) {
                    out +=
                        rec(
                            Priority.MEDIUM,
                            "Hash spill: требуется ~${humanBytes(need)} при work_mem ≈ ${humanBytes(wm)}",
                            "Риск выхода на диск. Подними work_mem точечно, подумай про disable hashagg для запроса, перепиши агрегаты/джойны.",
                            18.0,
                        )
                }
            }
        }
        return out
    }
}
