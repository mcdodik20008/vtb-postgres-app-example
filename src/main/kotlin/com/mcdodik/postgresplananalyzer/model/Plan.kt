package com.mcdodik.postgresplananalyzer.model

data class Plan(
    val root: PlanNode,
    val totalCost: Double
)
