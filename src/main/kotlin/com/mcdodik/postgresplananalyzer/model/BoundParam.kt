package com.mcdodik.postgresplananalyzer.model

data class BoundParam(
    val index: Int,
    val jdbcType: Int?,
    val value: Any?,                       // ← реальное значение
    val preview: String? = value?.toString()?.take(128) // ← удобство для логов
)

