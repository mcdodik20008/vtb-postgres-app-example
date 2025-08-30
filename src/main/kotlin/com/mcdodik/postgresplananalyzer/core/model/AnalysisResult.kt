package com.mcdodik.postgresplananalyzer.core.model

data class AnalysisResult(
    val plan: Plan,
    val planCost: Double,
    val estimates: PreflightEstimates,
    val recommendations: List<Recommendation>,
)
