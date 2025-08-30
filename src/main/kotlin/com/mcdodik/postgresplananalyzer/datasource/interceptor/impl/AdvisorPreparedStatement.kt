package com.mcdodik.postgresplananalyzer.datasource.interceptor.impl

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.CaptureContext
import com.mcdodik.postgresplananalyzer.datasource.interceptor.model.BoundParam
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.concurrent.ConcurrentHashMap

class AdvisorPreparedStatement(
    private val target: PreparedStatement,
    private val sql: String,
    private val sink: (BoundQuery) -> Unit,
    private val dataSourceId: String?,
) : PreparedStatement by target {
    private val params = ConcurrentHashMap<Int, BoundParam>()

    override fun setObject(
        parameterIndex: Int,
        x: Any?,
        targetSqlType: Int,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, targetSqlType, x?.toString()?.take(128))
        target.setObject(parameterIndex, x, targetSqlType)
    }

    override fun setObject(
        parameterIndex: Int,
        x: Any?,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, null, x?.toString()?.take(128))
        target.setObject(parameterIndex, x)
    }

    override fun setString(
        parameterIndex: Int,
        x: String?,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, Types.VARCHAR, x?.take(128))
        target.setString(parameterIndex, x)
    }

    override fun setLong(
        parameterIndex: Int,
        x: Long,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, Types.BIGINT, x.toString())
        target.setLong(parameterIndex, x)
    }

    override fun setInt(
        parameterIndex: Int,
        x: Int,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, Types.INTEGER, x.toString())
        target.setInt(parameterIndex, x)
    }

    override fun setBoolean(
        parameterIndex: Int,
        x: Boolean,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, Types.BOOLEAN, x.toString())
        target.setBoolean(parameterIndex, x)
    }

    override fun setTimestamp(
        parameterIndex: Int,
        x: java.sql.Timestamp?,
    ) {
        params[parameterIndex] = BoundParam(parameterIndex, Types.TIMESTAMP, x.toString())
        target.setTimestamp(parameterIndex, x)
    }

    private fun capture() {
        if (CaptureContext.isInternal.get() == true) return // не ловить EXPLAIN
        val bq =
            BoundQuery(
                sql = sql,
                params = params.values.sortedBy { it.index },
                dataSourceId = dataSourceId,
                capturedAtMs = System.currentTimeMillis(),
            )
        sink(bq)
    }

    override fun execute(): Boolean {
        capture()
        return target.execute()
    }

    override fun executeQuery(): ResultSet {
        capture()
        return target.executeQuery()
    }

    override fun executeUpdate(): Int {
        capture()
        return target.executeUpdate()
    }

    override fun executeBatch(): IntArray {
        capture()
        return target.executeBatch()
    }
}
