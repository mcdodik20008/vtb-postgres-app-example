package com.mcdodik.postgresplananalyzer.core.api

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

interface Rule {
    fun analyze(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): List<Recommendation>
}
