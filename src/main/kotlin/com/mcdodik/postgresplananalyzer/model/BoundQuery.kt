package com.mcdodik.postgresplananalyzer.model

data class BoundQuery(
    val sql: String,
    val params: List<BoundParam>,
    val dataSourceId: String?,
    val capturedAtMs: Long,
    val tags: Map<String, String> = emptyMap()
)

