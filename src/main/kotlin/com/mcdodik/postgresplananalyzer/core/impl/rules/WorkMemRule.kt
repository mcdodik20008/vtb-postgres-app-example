package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class WorkMemRule : BaseRule("work-mem") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val wm = workMemBytes(settings)
        var worstNeed: Long = 0
        var worstNode: String? = null
        var hashNeed: Long = 0
        var sortNeed: Long = 0

        walk(plan.root) { n ->
            val t = n.nodeType.lowercase()
            if (t == "sort") {
                val need = nodeBytes(n) * 2L
                sortNeed = maxOf(sortNeed, need)
                if (need > worstNeed) {
                    worstNeed = need
                    worstNode = "Sort"
                }
            } else if (t.contains("hash")) {
                val need = (nodeBytes(n) * 1.5).toLong()
                hashNeed = maxOf(hashNeed, need)
                if (need > worstNeed) {
                    worstNeed = need
                    worstNode = n.nodeType
                }
            }
        }

        if (worstNeed <= wm) return emptyList()

        val targetMb = ((worstNeed + (1L shl 20) - 1) / (1L shl 20)).toInt().coerceAtLeast(settings.workMemMb)
        val details =
            buildString {
                append("Требуется больше памяти для узла ${worstNode ?: "Sort/Hash"}: ~${humanBytes(worstNeed)}, ")
                append("при work_mem ≈ ${humanBytes(wm)}.\n")
                if (sortNeed > 0) append("- Наибольшая SORT-оценка: ~${humanBytes(sortNeed)}\n")
                if (hashNeed > 0) append("- Наибольшая HASH-оценка: ~${humanBytes(hashNeed)}\n")
                append("- Рекомендации: поднять work_mem для сессии до ~$targetMb MB (точечно), ")
                append("создать индекс под ORDER BY/AGG, протолкнуть LIMIT, уменьшить промежуточные наборы.")
            }
        return listOf(
            rec(
                Priority.MEDIUM,
                "Недостаточно work_mem для Sort/Hash",
                details,
                18.0,
            ),
        )
    }
}
