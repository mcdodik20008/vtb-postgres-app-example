package com.mcdodik.postgresplananalyzer.core.model

data class PreflightEstimates(
    val expectedTimeMs: Long,
    val scannedBytes: Long,
    val estimatedPagesRead: Long,
    val memoryPeakBytes: Long,
    val parallelPlanned: Boolean,
    val lockLevel: String,
)
