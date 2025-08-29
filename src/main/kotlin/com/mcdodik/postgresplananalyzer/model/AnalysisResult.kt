package com.mcdodik.postgresplananalyzer.model

data class AnalysisResult(
    val planCost: Double,
    val estimates: Estimates,
    val recommendations: List<Recommendation>
)

