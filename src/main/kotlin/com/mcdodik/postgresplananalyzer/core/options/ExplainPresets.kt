package com.mcdodik.postgresplananalyzer.core.options

object ExplainPresets {
    val CI =
        ExplainOptions(
            formatJson = true,
            costs = true,
            verbose = false,
            settings = true,
            summary = false,
            buffers = false,
            timing = false,
            wal = false,
            analyze = false,
        )

    val ADVISER =
        ExplainOptions(
            formatJson = true,
            costs = true,
            verbose = true,
            settings = true,
            summary = true,
            buffers = false,
            timing = false,
            wal = false,
            analyze = false,
        )
}
