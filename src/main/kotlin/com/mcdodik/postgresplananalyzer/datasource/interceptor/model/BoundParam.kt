package com.mcdodik.postgresplananalyzer.datasource.interceptor.model

data class BoundParam(
    val index: Int,
    val jdbcType: Int?,
    val value: Any?,
    val preview: String? = value?.toString()?.take(128),
)
