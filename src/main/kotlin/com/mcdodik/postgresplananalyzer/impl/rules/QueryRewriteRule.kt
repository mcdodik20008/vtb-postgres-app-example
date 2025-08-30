package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PredicateParser
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class QueryRewriteRule : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        val sql = query.sql.lowercase()
        val out = mutableListOf<Recommendation>()

        if (Regex("""select\s+\*""").containsMatchIn(sql))
            out += rec("query", "Избегайте SELECT *", "Выберите только нужные колонки.", 10.0)

        if (Regex("""\bwhere\s+\w+\s*::\w+\s*=\s*\?""").containsMatchIn(sql))
            out += rec(
                "query",
                "Избегайте кастов в WHERE",
                "Касты на колонке ломают индекс. Приводите параметр, не столбец.",
                20.0
            )

        if (Regex("""\bor\b""").containsMatchIn(sql) && !Regex("""\(\s*select""").containsMatchIn(sql))
            out += rec("query", "OR в WHERE", "Разделите запрос на UNION ALL с индексируемыми предикатами.", 15.0)

        PredicateParser.likePatterns(plan.root.filter).forEach { (col, pat) ->
            if (pat.startsWith("%"))
                out += rec(
                    "query",
                    "LIKE с ведущим %",
                    "Рассмотрите trigram/GIN (в рамках vanilla не советуем DDL) или перепишите на суффиксные поиски/индекс по reversed(col).",
                    15.0
                )
        }

        return out
    }

    private fun rec(cat: String, t: String, d: String, g: Double) =
        Recommendation(
            priority = if (g >= 20) Priority.MEDIUM else Priority.LOW,
            category = cat, title = t, detail = d, expectedGainPct = g
        )
}