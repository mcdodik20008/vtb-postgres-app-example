package com.mcdodik.postgresplananalyzer.api

import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.model.Recommendation

interface Rule {
    fun analyze(query: BoundQuery, plan: Plan, settings: PlannerSettings): List<Recommendation>
}