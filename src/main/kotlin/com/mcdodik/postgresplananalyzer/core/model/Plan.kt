package com.mcdodik.postgresplananalyzer.core.model

data class Plan(
    val root: PlanNode,
    val totalCost: Double,
)
