package com.mcdodik.postgresplananalyzer.core.model

import com.mcdodik.postgresplananalyzer.datasource.interceptor.model.BoundParam

data class BoundQuery(
    val sql: String,
    val params: List<BoundParam>,
    val dataSourceId: String?,
    val capturedAtMs: Long,
    val tags: Map<String, String> = emptyMap(),
)
