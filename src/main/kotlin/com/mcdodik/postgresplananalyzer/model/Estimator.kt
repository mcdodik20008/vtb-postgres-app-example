package com.mcdodik.postgresplananalyzer.model

class Estimator {
    fun estimate(plan: Plan, settings: PlannerSettings): Estimates {
        var pages = 0L
        fun walk(n: PlanNode) {
            // очень грубо: rows*width / 8KB, минимум = 1 страница на узел
            val bytes = (n.estimatedRows * n.planWidth.coerceAtLeast(1)).toLong()
            pages += (bytes / 8192L).coerceAtLeast(1)
            n.plans.forEach(::walk)
        }
        walk(plan.root)

        val memMb = (pages / 128).toInt() // ~128 страниц по 8KB ≈ 1MB
        val risks = mutableListOf<String>()
        if (memMb > settings.workMemMb) {
            risks += "Возможен spill сортировок/хэша (need≈${memMb}MB > work_mem=${settings.workMemMb}MB)"
        }
        return Estimates(ioPages = pages, workMemMbNeeded = memMb, risks = risks)
    }
}