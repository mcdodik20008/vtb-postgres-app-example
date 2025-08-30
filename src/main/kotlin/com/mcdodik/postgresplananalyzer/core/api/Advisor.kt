package com.mcdodik.postgresplananalyzer.core.api

import com.mcdodik.postgresplananalyzer.core.model.AnalysisResult
import com.mcdodik.postgresplananalyzer.core.model.BoundQuery

interface Advisor {
    fun examine(query: BoundQuery): AnalysisResult
}
