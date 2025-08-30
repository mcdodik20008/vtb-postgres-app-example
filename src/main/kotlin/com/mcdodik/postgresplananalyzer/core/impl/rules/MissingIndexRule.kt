package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class MissingIndexRule(
    private val minPages: Long = 5_000, // ~ 40 MiB
    private val pageSize: Int = 8192,
    private val maxColsInSuggestion: Int = 3,
) : BaseRule("index") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val out = mutableListOf<Recommendation>()
        walk(plan.root) { n ->
            if (n.nodeType == "Seq Scan") {
                val pg = nodePages(n, pageSize)
                val eq = equalityColumns(n.filter)
                val rng = rangeColumns(n.filter)
                val likes = likePatterns(n.filter)

                if (pg >= minPages && (eq.isNotEmpty() || rng.isNotEmpty() || likes.isNotEmpty())) {
                    val rel = n.relationName ?: "target table"
                    val eqCols = eq.take(maxColsInSuggestion)
                    val rngCols = rng.take(maxColsInSuggestion)
                    val likeStarts = likes.filter { !it.second.startsWith("%") } // только 'abc%'
                    val likeLeading = likes.filter { it.second.startsWith("%") } // '%abc'

                    val sb = StringBuilder("Полный скан ~$pg страниц. Рекомендации:\n")
                    if (eqCols.isNotEmpty()) {
                        sb.append("- B-Tree индекс по равенствам: (${eqCols.joinToString()})\n")
                    }
                    if (rngCols.isNotEmpty()) {
                        sb.append("- B-Tree индекс по диапазонам (leading колонки): (${rngCols.joinToString()})\n")
                    }
                    if (likeStarts.isNotEmpty()) {
                        sb.append(
                            "- Индекс под LIKE 'abc%%' (btree или btree text_pattern_ops) по: (${likeStarts.joinToString { it.first }})\n",
                        )
                    }
                    if (likeLeading.isNotEmpty()) {
                        sb.append("- Для LIKE '%%abc' использовать GIN с pg_trgm: CREATE INDEX ON $rel USING gin(col gin_trgm_ops)\n")
                    }
                    sb.append("- Рассмотреть PARTIAL INDEX, если предикат стабильный\n")

                    out +=
                        rec(
                            if (pg >= 50_000) Priority.HIGH else Priority.MEDIUM,
                            "Возможен отсутствующий индекс для $rel",
                            sb.toString(),
                            if (pg >= 50_000) 45.0 else 25.0,
                        )
                }
            }
        }
        return out
    }
}
