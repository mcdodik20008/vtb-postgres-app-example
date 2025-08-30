package com.mcdodik.postgresplananalyzer.core.impl.rules

import com.mcdodik.postgresplananalyzer.core.api.Rule
import com.mcdodik.postgresplananalyzer.core.model.PlanNode
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.Priority
import com.mcdodik.postgresplananalyzer.core.model.Recommendation

abstract class BaseRule(
    private val category: String,
) : Rule {
    // ==== обход дерева ====
    protected inline fun walk(
        root: PlanNode,
        crossinline fn: (PlanNode) -> Unit,
    ) {
        val stack = ArrayDeque<PlanNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            fn(n)
            val kids = n.plans
            for (i in kids.size - 1 downTo 0) {
                stack.add(kids[i])
            }
        }
    }

    protected fun hasNode(
        root: PlanNode,
        predicate: (PlanNode) -> Boolean,
    ): Boolean {
        val stack = ArrayDeque<PlanNode>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val n = stack.removeLast()
            if (predicate(n)) return true
            val kids = n.plans
            for (i in kids.size - 1 downTo 0) stack.add(kids[i])
        }
        return false
    }

    // ==== метрики узла ====
    protected fun nodeBytes(n: PlanNode): Long {
        val estimatedRows = if (n.estimatedRows > 0) n.estimatedRows else 0.0
        val planWidth = if (n.planWidth > 0) n.planWidth else 1
        return (estimatedRows * planWidth).toLong()
    }

    protected fun nodePages(
        n: PlanNode,
        pageSize: Int = 8192,
    ): Long = nodeBytes(n) / pageSize

    // ==== чтение настроек ====
    protected fun workMemBytes(settings: PlannerSettings): Long = settings.workMemMb.toLong() * 1024 * 1024

    protected fun humanBytes(b: Long): String =
        when {
            b >= (1L shl 30) -> "${b / (1L shl 30)} GiB"
            b >= (1L shl 20) -> "${b / (1L shl 20)} MiB"
            b >= (1L shl 10) -> "${b / (1L shl 10)} KiB"
            else -> "$b B"
        }

    // ==== парсинг предикатов (простые эвристики) ====
    private val reEq = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s*=\s*(?:\$\d+|\?|\d+|'[^']*')""")
    private val reRng = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s*(?:>=|<=|>|<)\s*(?:\$\d+|\?|\d+)""")
    private val reLike = Regex("""(?i)\b([a-zA-Z_][\w.]*)\s+LIKE\s+'([^']+)'""")

    protected fun equalityColumns(filter: String?): List<String> = reEq.findAll(filter.orEmpty()).map { it.groupValues[1] }.toList()

    protected fun rangeColumns(filter: String?): List<String> = reRng.findAll(filter.orEmpty()).map { it.groupValues[1] }.toList()

    protected fun likePatterns(filter: String?): List<Pair<String, String>> =
        reLike.findAll(filter.orEmpty()).map { it.groupValues[1] to it.groupValues[2] }.toList()

    // -------- извлечение ORDER BY из SQL, если в PlanNode нет Sort Key --------
    private val orderByRegex = Regex("""(?is)\border\s+by\s+(.+?)(?:\blimit\b|\boffset\b|$)""")

    protected fun sortKeysFromSql(sql: String): List<String> {
        val m = orderByRegex.find(sql) ?: return emptyList()
        val payload = m.groupValues[1].trim()
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (ch in payload) {
            when (ch) {
                '(' -> {
                    depth++
                    sb.append(ch)
                }

                ')' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    sb.append(ch)
                }

                ',' ->
                    if (depth == 0) {
                        parts += sb.toString()
                        sb.setLength(0)
                    } else {
                        sb.append(ch)
                    }

                else -> sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) parts += sb.toString()
        return parts.map { cleanupOrderExpr(it) }.filter { it.isNotBlank() }
    }

    private fun cleanupOrderExpr(exprRaw: String): String =
        exprRaw
            .replace(
                Regex("(?i)\\basc\\b|\\bdesc\\b|\\bnulls\\s+first\\b|\\bnulls\\s+last\\b|\\bcollate\\s+\\S+"),
                "",
            ).replace(Regex("\\s+"), " ")
            .trim()
            .trim(',')

    // ==== фабрика Recommendation ====
    protected fun rec(
        priority: Priority,
        title: String,
        detail: String,
        expectedGainPct: Double,
    ) = Recommendation(
        priority = priority,
        category = category,
        title = title,
        detail = detail,
        expectedGainPct = expectedGainPct,
    )
}
