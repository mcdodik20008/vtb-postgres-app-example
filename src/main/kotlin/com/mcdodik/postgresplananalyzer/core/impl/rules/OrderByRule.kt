package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class OrderByRule : BaseRule("order-by") {
    private val limitRegex = Regex("""(?i)\blimit\s+(\d+)""")
    private val randomRegex = Regex("""(?i)\border\s+by\s+random\(\)""")

    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val recs = mutableListOf<Recommendation>()
        val orderKeys = sortKeysFromSql(query.sql)
        val hasRandom = randomRegex.containsMatchIn(query.sql)
        val limit =
            limitRegex
                .find(query.sql)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()

        var hasTopSort = false
        var topRows: Long? = null
        // ищем верхний Sort и его вход (для оценки «много сортируем»)
        walk(plan.root) { n ->
            if (n.nodeType == "Sort" && !hasTopSort) {
                hasTopSort = true
                topRows = n.estimatedRows.toLong()
            }
        }

        if (hasRandom) {
            recs +=
                rec(
                    Priority.HIGH,
                    "ORDER BY random() крайне дорого",
                    "Случайная сортировка заставляет полный сорт всего набора. Рекомендуется предвыбирать random id, " +
                        "использовать TABLESAMPLE/метку случайности в данных, или OFFSET-based выборку с псевдослучайным seed.",
                    60.0,
                )
        }

        if (hasTopSort) {
            val keys = if (orderKeys.isEmpty()) "<unknown>" else orderKeys.joinToString(", ")
            val details = StringBuilder("Обнаружена сортировка по: $keys.")
            if (limit != null && topRows != null && topRows!! > limit * 10) {
                details.append(" Сортируется значительно больше строк, чем возвращается (LIMIT $limit) — сделай limit pushdown/индекс.")
            }
            if (orderKeys.any { it.contains('(') }) {
                details.append(" В ORDER BY есть функция — нужен функциональный индекс под выражение.")
            }
            recs +=
                rec(
                    Priority.MEDIUM,
                    "Неоптимальная сортировка",
                    details.toString() + " Рекомендации: индекс под ORDER BY (правильный порядок/направление), " +
                        "удалить лишние SORT, протолкнуть LIMIT.",
                    18.0,
                )
        }
        return recs
    }
}
