package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

class FunctionVolatilityRule : BaseRule("function-volatility") {
    override fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation> {
        val hasFunc = hasNode(plan.root) { it.nodeType == "Function Scan" || it.nodeType == "Result" }
        return if (hasFunc) {
            listOf(
                rec(
                    Priority.LOW,
                    "Проверь VOLATILITY у функций",
                    "IMMUTABLE/STABLE улучшит индексацию по выражениям и упростит планы.",
                    5.0,
                ),
            )
        } else {
            emptyList()
        }
    }
}
