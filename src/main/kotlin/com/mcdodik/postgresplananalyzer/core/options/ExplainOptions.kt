package com.mcdodik.postgresplananalyzer.core.options

data class ExplainOptions(
    val formatJson: Boolean = true,
    val costs: Boolean = true,
    val verbose: Boolean = true,
    val settings: Boolean = true,
    val summary: Boolean = true,
    val buffers: Boolean = false,
    val timing: Boolean = false,
    val wal: Boolean = false,
    val analyze: Boolean = false,
) {
    fun toClause(): String {
        val opts =
            buildList {
                add("FORMAT ${if (formatJson) "JSON" else "TEXT"}")
                add("COSTS ${on(costs)}")
                add("VERBOSE ${on(verbose)}")
                add("SETTINGS ${on(settings)}")
                add("SUMMARY ${on(summary)}")
                add("BUFFERS ${on(buffers)}")
                add("TIMING ${on(timing)}")
                add("WAL ${on(wal)}")
                add("ANALYZE ${on(analyze)}")
            }.joinToString(", ")
        return "EXPLAIN ($opts)"
    }

    private fun on(b: Boolean) = if (b) "true" else "false"
}
