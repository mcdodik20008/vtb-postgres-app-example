package com.mcdodik.postgresplananalyzer.core.model

data class Recommendation(
    val priority: Priority,
    val category: String,
    val title: String,
    val detail: String,
    val expectedGainPct: Double,
    val ddl: String? = null,
)
