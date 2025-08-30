package com.mcdodik.postgresplananalyzer.core.model

object CaptureContext {
    val isInternal: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
}
