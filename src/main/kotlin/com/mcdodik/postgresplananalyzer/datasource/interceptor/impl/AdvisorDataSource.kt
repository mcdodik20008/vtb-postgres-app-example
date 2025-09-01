package com.mcdodik.postgresplananalyzer.datasource.interceptor.impl

import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import javax.sql.DataSource

class AdvisorDataSource(
    private val target: DataSource,
    private val sink: (BoundQuery) -> Unit,
    private val dataSourceId: String? = null,
) : DataSource by target {
    override fun getConnection(): Connection = wrap(target.connection)

    override fun getConnection(
        username: String?,
        password: String?,
    ): Connection = wrap(target.getConnection(username, password))

    private fun wrap(conn: Connection): Connection =
        Proxy.newProxyInstance(
            conn.javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            if (method.name == "prepareStatement" && args?.isNotEmpty() == true && args[0] is String) {
                val sql = args[0] as String
                val ps = method.invoke(conn, *args) as PreparedStatement
                return@newProxyInstance AdvisorPreparedStatement(ps, sql, sink, dataSourceId)
            }
            method.invoke(conn, *(args ?: emptyArray()))
        } as Connection
}
