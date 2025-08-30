package com.mcdodik.postgresplananalyzer.impl.rules

import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Priority
import com.mcdodik.postgresplananalyzer.model.Recommendation

class NPlusOneRule(
    private val sameQueryBurstThreshold: Int = 50
) : Rule {
    override fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation> {
        // Допущение: у тебя есть внешний счётчик одинаковых normalizedSql за окно времени.
        // Здесь просто пример возвращаемой рекомендации.
        return if (query.tags["burstCount"]?.toIntOrNull()?.let { it >= sameQueryBurstThreshold } == true) {
            listOf(
                Recommendation(
                    priority = Priority.MEDIUM,
                    category = "query",
                    title = "Подозрение на N+1",
                    detail = "Один и тот же SELECT выполняется пакетно (${query.tags["burstCount"]}) раз за короткий интервал. Рассмотрите JOIN/IN/CTE для агрегации.",
                    expectedGainPct = 30.0
                )
            )
        } else emptyList()
    }
}
