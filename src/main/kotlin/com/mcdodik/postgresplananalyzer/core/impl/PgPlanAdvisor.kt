package com.mcdodik.postgresplananalyzer.core.impl

import com.mcdodik.postgresplananalyzer.core.api.Advisor
import com.mcdodik.postgresplananalyzer.core.api.Rule
import com.mcdodik.postgresplananalyzer.core.model.AnalysisResult
import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.CaptureContext
import com.mcdodik.postgresplananalyzer.core.model.Plan
import com.mcdodik.postgresplananalyzer.core.model.PlanNode
import com.mcdodik.postgresplananalyzer.core.model.PlannerSettings
import com.mcdodik.postgresplananalyzer.core.model.PreflightEstimates
import com.mcdodik.postgresplananalyzer.core.options.ExplainOptions
import com.mcdodik.postgresplananalyzer.core.options.ExplainPresets
import com.mcdodik.postgresplananalyzer.datasource.interceptor.model.BoundParam
import com.mcdodik.postgresplananalyzer.instastructure.PgExplainParser
import java.sql.Types
import javax.sql.DataSource
import kotlin.math.max

/**
 * PgPlanAdvisor — предзапускной советник: делает только EXPLAIN (без ANALYZE),
 * оценивает время/IO/память/локи и прогоняет правила.
 */
class PgPlanAdvisor(
    private val explainDs: DataSource,
    private val rules: List<Rule> = DefaultRuleset.build(),
    private val planner: PlannerSettings = PlannerSettings(),
    private val explainOptions: ExplainOptions = ExplainPresets.ADVISER,
    // Конвертация cost→ms: t_ms ≈ a * total_cost + b.
    private val costToMsSlopeA: Double = 1.0,
    private val costToMsInterceptB: Double = 0.0,
    private val pageSizeBytes: Int = 8192,
) : Advisor {
    override fun examine(query: BoundQuery): AnalysisResult {
        val plan = explain(query, explainOptions)
        val estimates = estimatePreflight(query, plan, planner)
        val recs =
            rules
                .flatMap { it.analyze(query, plan, planner) }
                .sortedByDescending { it.expectedGainPct }
        return AnalysisResult(
            plan = plan,
            planCost = plan.totalCost,
            estimates = estimates,
            recommendations = recs,
        )
    }

    private fun estimatePreflight(
        query: BoundQuery,
        plan: Plan,
        settings: PlannerSettings,
    ): PreflightEstimates {
        var scannedBytes = 0L
        var memPeak = 0L
        var hasGather = false

        fun nodeBytes(n: PlanNode): Long = (max(0.0, n.estimatedRows) * max(1, n.planWidth)).toLong()

        fun walk(
            n: PlanNode,
            f: (PlanNode) -> Unit,
        ) {
            val st = ArrayDeque<PlanNode>()
            st.add(n)
            while (st.isNotEmpty()) {
                val cur = st.removeLast()
                f(cur)
                for (i in cur.plans.size - 1 downTo 0) st.add(cur.plans[i])
            }
        }

        walk(plan.root) { n ->
            val t = n.nodeType.lowercase()

            if (t.contains("scan")) scannedBytes += nodeBytes(n)

            if (t == "sort") {
                memPeak += nodeBytes(n) * 2L
            } else if (t.contains("hash")) {
                memPeak += (nodeBytes(n) * 1.5).toLong()
            }

            if (t.contains("gather")) hasGather = true
        }

        val pages = scannedBytes / pageSizeBytes
        val expectedMs =
            ((costToMsSlopeA * plan.totalCost) + costToMsInterceptB)
                .coerceAtLeast(0.0)
                .toLong()

        val lock = inferLockLevel(query.sql, plan)

        return PreflightEstimates(
            expectedTimeMs = expectedMs,
            scannedBytes = scannedBytes,
            estimatedPagesRead = pages,
            memoryPeakBytes = memPeak,
            parallelPlanned = hasGather,
            lockLevel = lock,
        )
    }

    private fun inferLockLevel(
        sql: String,
        plan: Plan,
    ): String {
        val s = sql.trimStart().lowercase()
        return when {
            s.startsWith("update") || s.startsWith("delete") -> "ROW EXCLUSIVE"
            s.startsWith("insert") -> "ROW EXCLUSIVE"
            s.startsWith("select") && s.contains("for update") -> "ROW SHARE"
            s.startsWith("select") -> "ACCESS SHARE"
            else -> "ACCESS SHARE"
        }
    }

    private fun explain(
        q: BoundQuery,
        opts: ExplainOptions,
    ): Plan {
        val sqlExplain = "${opts.toClause()} ${q.sql}"
        CaptureContext.isInternal.set(true)
        try {
            explainDs.connection.use { c ->
                c.prepareStatement(sqlExplain).use { ps ->
                    q.params.sortedBy { it.index }.forEach { p -> bindParam(ps, p) }
                    ps.executeQuery().use { rs ->
                        check(rs.next()) { "EXPLAIN returned no rows" }
                        val json = rs.getString(1)
                        return PgExplainParser.parseExplainJson(json)
                    }
                }
            }
        } finally {
            CaptureContext.isInternal.set(false)
        }
    }

    /** Унифицированная привязка параметров PreparedStatement. */
    private fun bindParam(
        ps: java.sql.PreparedStatement,
        p: BoundParam,
    ) {
        val v = p.value
        when {
            v == null -> ps.setNull(p.index, p.jdbcType ?: Types.NULL)
            p.jdbcType != null -> ps.setObject(p.index, v, p.jdbcType)
            else ->
                when (v) {
                    is Int -> ps.setInt(p.index, v)
                    is Long -> ps.setLong(p.index, v)
                    is Short -> ps.setShort(p.index, v)
                    is Byte -> ps.setByte(p.index, v)
                    is Boolean -> ps.setBoolean(p.index, v)
                    is String -> ps.setString(p.index, v)
                    is Double -> ps.setDouble(p.index, v)
                    is Float -> ps.setFloat(p.index, v)
                    is java.math.BigDecimal -> ps.setBigDecimal(p.index, v)
                    is java.sql.Timestamp -> ps.setTimestamp(p.index, v)
                    is java.sql.Date -> ps.setDate(p.index, v)
                    is java.sql.Time -> ps.setTime(p.index, v)
                    is java.time.LocalDate, is java.time.LocalDateTime, is java.time.OffsetDateTime ->
                        ps.setObject(
                            p.index,
                            v,
                        )

                    is IntArray ->
                        ps.connection
                            .createArrayOf("int4", v.toTypedArray())
                            .also { ps.setArray(p.index, it) }

                    is LongArray ->
                        ps.connection
                            .createArrayOf("int8", v.toTypedArray())
                            .also { ps.setArray(p.index, it) }

                    is Array<*> -> {
                        val arrType =
                            when (v.firstOrNull()) {
                                is Int -> "int4"
                                is Long -> "int8"
                                is String -> "text"
                                else -> "text"
                            }
                        ps.connection.createArrayOf(arrType, v).also { ps.setArray(p.index, it) }
                    }

                    else -> ps.setObject(p.index, v)
                }
        }
    }
}
