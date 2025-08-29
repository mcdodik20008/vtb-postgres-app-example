package com.mcdodik.postgresplananalyzer.model

object CaptureContext {
    val isInternal: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
}