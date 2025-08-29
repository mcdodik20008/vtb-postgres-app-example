package com.mcdodik.postgresplananalyzer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PostgresPlanAnalyzerApplication

fun main(args: Array<String>) {
    runApplication<PostgresPlanAnalyzerApplication>(*args)
}
