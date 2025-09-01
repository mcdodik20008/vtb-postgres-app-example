package com.mcdodik.postgresplananalyzer

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
    fromApplication<PostgresPlanAnalyzerApplication>().with(TestcontainersConfiguration::class).run(*args)
}
