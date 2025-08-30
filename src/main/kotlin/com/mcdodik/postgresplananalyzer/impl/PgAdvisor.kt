package com.mcdodik.postgresplananalyzer.impl

import com.mcdodik.postgresplananalyzer.api.Advisor
import com.mcdodik.postgresplananalyzer.api.Rule
import com.mcdodik.postgresplananalyzer.instastructure.PgExplainParser
import com.mcdodik.postgresplananalyzer.model.AnalysisResult
import com.mcdodik.postgresplananalyzer.model.BoundParam
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.CaptureContext
import com.mcdodik.postgresplananalyzer.model.Estimator
import com.mcdodik.postgresplananalyzer.model.Plan
import com.mcdodik.postgresplananalyzer.model.PlannerSettings
import java.sql.Types
import javax.sql.DataSource

class PgAdvisor(
    private val explainDs: DataSource,
    private val rules: List<Rule>,
    private val planner: PlannerSettings = PlannerSettings()
) : Advisor {

    override fun analyze(query: BoundQuery): AnalysisResult {
        val plan = explain(query)
        val estimates = Estimator().estimate(plan, planner)
        val recs = rules
            .flatMap { it.analyze(query, plan, planner) }
            .sortedByDescending { it.expectedGainPct } // одной сортировки достаточно
        return AnalysisResult(plan.totalCost, estimates, recs)
    }

    private fun explain(q: BoundQuery): Plan {
        val sqlExplain = "EXPLAIN (FORMAT JSON, COSTS true, VERBOSE false, BUFFERS false, TIMING false) ${q.sql}"
        CaptureContext.isInternal.set(true)
        try {
            explainDs.connection.use { c ->
                c.prepareStatement(sqlExplain).use { ps ->
                    // ВАЖНО: биндим РЕАЛЬНЫЕ значения в том же порядке
                    q.params.sortedBy { it.index }.forEach { p -> bindParam(ps, p) }
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) error("EXPLAIN returned no rows")
                        val json = rs.getString(1)
                        return PgExplainParser.parseExplainJson(json)
                    }
                }
            }
        } finally {
            CaptureContext.isInternal.set(false)
        }
    }

    /** Унифицированная биндер-функция для параметра PreparedStatement. */
    private fun bindParam(ps: java.sql.PreparedStatement, p: BoundParam) {
        val v = p.value
        when {
            v == null -> ps.setNull(p.index, p.jdbcType ?: Types.NULL)

            // Если пришёл явный JDBC-тип — используем его
            p.jdbcType != null -> ps.setObject(p.index, v, p.jdbcType)

            // Иначе — подбираем сеттер по рантайм-типу
            else -> when (v) {
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
                is java.time.LocalDate, is java.time.LocalDateTime, is java.time.OffsetDateTime -> ps.setObject(p.index, v)
                is IntArray -> ps.connection.createArrayOf("int4", v.toTypedArray()).also { ps.setArray(p.index, it) }
                is LongArray -> ps.connection.createArrayOf("int8", v.toTypedArray()).also { ps.setArray(p.index, it) }
                is Array<*> -> {
                    // попытка угадать pg-тип массива по первому элементу
                    val arrType = when (v.firstOrNull()) {
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
