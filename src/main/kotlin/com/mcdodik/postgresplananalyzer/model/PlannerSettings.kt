package com.mcdodik.postgresplananalyzer.model

data class PlannerSettings(
    val randomPageCost: Double = 4.0,
    val seqPageCost: Double = 1.0,
    val workMemMb: Int = 64
)