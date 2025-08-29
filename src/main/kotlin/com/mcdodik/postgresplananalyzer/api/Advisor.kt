package com.mcdodik.postgresplananalyzer.api

import com.mcdodik.postgresplananalyzer.model.AnalysisResult
import com.mcdodik.postgresplananalyzer.model.BoundQuery

interface Advisor {
    fun analyze(query: BoundQuery): AnalysisResult
}

