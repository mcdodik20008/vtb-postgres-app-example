package com.mcdodik.postgresplananalyzer.model

data class PlanNode(
    val nodeType: String,
    val relationName: String? = null,
    val schema: String? = null,
    val alias: String? = null,
    val filter: String? = null,
    val indexCond: String? = null,
    val plans: List<PlanNode> = emptyList(),
    val estimatedRows: Double = 0.0,
    val planWidth: Int = 0,
    val totalCost: Double = 0.0
)