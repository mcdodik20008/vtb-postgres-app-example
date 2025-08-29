package com.mcdodik.postgresplananalyzer.model

data class Estimates(val ioPages: Long, val workMemMbNeeded: Int, val risks: List<String>)
