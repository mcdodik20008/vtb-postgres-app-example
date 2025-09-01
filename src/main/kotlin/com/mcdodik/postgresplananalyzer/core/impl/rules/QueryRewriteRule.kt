package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class QueryRewriteRule : BaseRule("rewrite") {
    private val orRegex = Regex("""(?i)\b(\w+)\s*=\s*(?:\$\d+|\?|\d+|'[^']*')\s+OR\s+(\w+)\s*=\s*(?:\$\d+|\?|\d+|'[^']*')""")
    private val funcOnCol = Regex("""(?i)(\w+)\s*\(\s*([a-zA-Z_][\w.]*)\s*\)""") // lower(col), date_trunc(col) и т.п.
    private val leadingWildcard = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s+LIKE\s+'%[^']*'""")
    private val notIn = Regex("""(?i)\bNOT\s+IN\s*\(""")

    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        val filterTexts = mutableSetOf<String>()
        walk(plan.root) { if (it.filter != null) filterTexts += it.filter!! }
        val filter = (filterTexts.joinToString(" AND ")).ifBlank { query.sql }

        if (orRegex.containsMatchIn(filter)) {
            recs +=
                rec(
                    Priority.MEDIUM,
                    "OR-предикат может мешать индексации",
                    "Рассмотри перепись в UNION ALL двух индексуемых запросов или составной индекс с правильным порядком колонок.",
                    12.0,
                )
        }
        if (leadingWildcard.containsMatchIn(filter)) {
            recs +=
                rec(
                    Priority.MEDIUM,
                    "LIKE с ведущим '%' не использует B-Tree",
                    "Используй GIN + pg_trgm (gin_trgm_ops) или перепиши предикат. Для 'abc%%' B-Tree возможен (text_pattern_ops).",
                    20.0,
                )
        }
        if (funcOnCol.containsMatchIn(filter)) {
            recs +=
                rec(
                    Priority.LOW,
                    "Функция на колонке в WHERE",
                    "Нужен функциональный индекс по выражению или переписать: применить функцию к константе/вынести предобработку.",
                    8.0,
                )
        }
        if (notIn.containsMatchIn(filter)) {
            recs +=
                rec(
                    Priority.LOW,
                    "NOT IN часто ведёт к полным сканам",
                    "Рассмотри перепись на anti-JOIN (LEFT JOIN ... WHERE right IS NULL) или NOT EXISTS.",
                    6.0,
                )
        }
        return recs
    }
}
