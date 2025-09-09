package com.mcdodik.postgresplananalyzer.rules

import com.mcdodik.postgresplananalyzer.core.impl.PgPlanAdvisor
import com.mcdodik.postgresplananalyzer.core.model.BoundQuery
import com.mcdodik.postgresplananalyzer.core.model.PlanNode
import com.mcdodik.postgresplananalyzer.datasource.interceptor.impl.AdvisorDataSource
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
class TwoMillionUsersIndexAdviceIT {
    companion object {
        @Container
        @JvmStatic
        val pg =
            PostgreSQLContainer("postgres:15-alpine")
                .withDatabaseName("app")
                .withUsername("app")
                .withPassword("app")
                .withStartupTimeout(Duration.ofMinutes(3))
                .withCommand(
                    "postgres",
                    "-c",
                    "shared_preload_libraries=pg_stat_statements",
                    "-c",
                    "compute_query_id=on",
                    "-c",
                    "pg_stat_statements.track=all",
                    "-c",
                    "pg_stat_statements.max=10000",
                    "-c",
                    "pg_stat_statements.save=off",
                )

        private fun hikari(
            url: String,
            user: String,
            pass: String,
            name: String,
        ) = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = url
                username = user
                password = pass
                maximumPoolSize = 4
                poolName = name
            },
        )
    }

    private lateinit var rawDs: HikariDataSource
    private lateinit var explainDs: HikariDataSource
    private lateinit var wrappedDs: javax.sql.DataSource

    private val sinkQueue = LinkedBlockingQueue<BoundQuery>()
    private val sink: (BoundQuery) -> Unit = { bq -> sinkQueue.offer(bq) }

    @BeforeEach
    fun setUp() {
        rawDs = hikari(pg.jdbcUrl, pg.username, pg.password, "raw-ds")
        explainDs = hikari(pg.jdbcUrl, pg.username, pg.password, "explain-ds")
        wrappedDs = AdvisorDataSource(rawDs, sink, dataSourceId = "pg-test")

        // включаем расширение + чистим статистику
        rawDs.connection.use { c ->
            c.createStatement().use { st ->
                st.execute("CREATE EXTENSION IF NOT EXISTS pg_stat_statements;")
                st.execute("SELECT pg_stat_statements_reset();")
            }
        }

        // схема + данные
        rawDs.connection.use { c ->
            c.createStatement().use { st ->
                st.execute(
                    """
                    DROP TABLE IF EXISTS users;
                    CREATE TABLE users(
                        id   bigserial PRIMARY KEY,
                        name text NOT NULL,
                        age  int  NOT NULL
                    );
                    -- 2 000 000 строк: возраст 18..80 равномерно
                    INSERT INTO users(name, age)
                    SELECT 'user-' || g, 18 + (g % 63)
                    FROM generate_series(1, 2000000) AS g;
                    ANALYZE users;
                    """.trimIndent(),
                )
            }
        }
    }

    @AfterEach
    fun tearDown() {
        wrappedDs.let { }
        explainDs.close()
        rawDs.close()
    }

    /** Вспомогалка: найти первый узел с типом из множества */
    private fun findNode(
        root: PlanNode,
        types: Set<String>,
    ): PlanNode? {
        fun dfs(n: PlanNode): PlanNode? {
            if (n.nodeType in types) return n
            n.plans.forEach { child -> dfs(child)?.let { return it } }
            return null
        }
        return dfs(root)
    }

    @Test
    fun `seq scan vs index scan on users-age with pg_stat_statements visibility`() {
        val ageValue = 42

        // --- 1) до индекса: выполняем запрос и перехватываем BoundQuery ---
        wrappedDs.connection.use { conn ->
            conn.prepareStatement("select count(*) from users where age = ?").use { ps ->
                ps.setInt(1, ageValue)
                ps.executeQuery().use { rs -> assertTrue(rs.next()) }
            }
        }
        val capturedBefore = sinkQueue.poll(10, TimeUnit.SECONDS)
        assertNotNull(capturedBefore, "Перехватчик не вернул BoundQuery (до индекса)")

        val advisor = PgPlanAdvisor(explainDs, rules = emptyList())
        val before = advisor.examine(capturedBefore)
        assertTrue(before.planCost > 0.0, "Ожидали ненулевой Total Cost до индекса")
        val seqBefore = findNode(before.plan.root, setOf("Seq Scan"))
        assertNotNull(seqBefore, "Ожидали Seq Scan до индекса")

        // --- 2) создаём индекс и повторяем запрос ---
        rawDs.connection.use { c -> c.createStatement().use { it.execute("CREATE INDEX idx_users_age ON users(age)") } }

        wrappedDs.connection.use { conn ->
            conn.prepareStatement("select count(*) from users where age = ?").use { ps ->
                ps.setInt(1, ageValue)
                ps.executeQuery().use { rs -> assertTrue(rs.next()) }
            }
        }
        val capturedAfter = sinkQueue.poll(10, TimeUnit.SECONDS)
        assertNotNull(capturedAfter, "Перехватчик не вернул BoundQuery (после индекса)")

        val after = advisor.examine(capturedAfter)
        assertTrue(after.planCost > 0.0)

        // plan cost должен упасть
        println("Plan cost: before=${before.planCost}, after=${after.planCost}")
        println(before.plan)
        println(after.plan)
        assertTrue(after.planCost < before.planCost, "Ожидали падение стоимости")

        // и тип скана должен смениться на Index/Bitmap
        val idxAfter =
            findNode(
                after.plan.root,
                setOf("Index Scan", "Index Only Scan", "Bitmap Heap Scan", "Bitmap Index Scan"),
            )
        assertNotNull(idxAfter, "Ожидали индексный план после создания индекса")

        rawDs.connection.use { c ->
            c
                .prepareStatement(
                    """
                    SELECT queryid, calls, total_exec_time, mean_exec_time, shared_blks_read, temp_blks_written, query
                    FROM pg_stat_statements
                    WHERE query LIKE 'select count(*) from users where age = $1%'
                    ORDER BY calls DESC
                    LIMIT 1
                    """.trimIndent(),
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            val qid = rs.getLong("queryid")
                            val calls = rs.getLong("calls")
                            val meanMs = rs.getDouble("mean_exec_time")
                            val readBlks = rs.getLong("shared_blks_read")
                            val tempBlks = rs.getLong("temp_blks_written")
                            println(
                                "pgss: queryid=$qid calls=$calls mean=${"%.2f".format(meanMs)}ms read_blks=$readBlks temp_blks=$tempBlks",
                            )
                        } else {
                            println("pgss: запись не найдена (нормализация/версия PG могут отличаться)")
                        }
                    }
                }
        }
    }
}
