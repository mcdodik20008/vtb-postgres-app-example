package com.mcdodik.postgresplananalyzer.rules


import com.mcdodik.postgresplananalyzer.api.Advisor
import com.mcdodik.postgresplananalyzer.impl.PgAdvisor
import com.mcdodik.postgresplananalyzer.dsinterceptor.AdvisorDataSource
import com.mcdodik.postgresplananalyzer.model.BoundQuery
import com.mcdodik.postgresplananalyzer.model.PlanNode
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class TwoMillionUsersIndexAdviceIT {

    companion object {
        @Container
        @JvmStatic
        val pg = PostgreSQLContainer("postgres:15-alpine")
            .withDatabaseName("app")
            .withUsername("app")
            .withPassword("app")
            .withStartupTimeout(Duration.ofMinutes(3))

        private fun hikari(url: String, user: String, pass: String, name: String) =
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = url; username = user; password = pass
                maximumPoolSize = 4
                poolName = name
            })
    }

    private lateinit var rawDs: HikariDataSource       // «боевой» пул (перехватываем)
    private lateinit var explainDs: HikariDataSource   // отдельный пул для EXPLAIN
    private lateinit var wrappedDs: javax.sql.DataSource

    private val sinkQueue = LinkedBlockingQueue<BoundQuery>()
    private val sink: (BoundQuery) -> Unit = { bq -> sinkQueue.offer(bq) }

    @BeforeEach
    fun setUp() {
        rawDs = hikari(pg.jdbcUrl, pg.username, pg.password, "raw-ds")
        explainDs = hikari(pg.jdbcUrl, pg.username, pg.password, "explain-ds")
        wrappedDs = AdvisorDataSource(rawDs, sink, dataSourceId = "pg-test")

        // схема + данные
        rawDs.connection.use { c ->
            c.createStatement().use { st ->
                st.execute(
                    """
                    drop table if exists users;
                    create table users(
                        id   bigserial primary key,
                        name text not null,
                        age  int  not null
                    );
                    -- 2 000 000 строк: равномерный возраст 18..80
                    insert into users(name, age)
                    select 'user-' || g, 18 + (g % 63)
                    from generate_series(1, 2000000) as g;
                    analyze users;
                """.trimIndent()
                )
            }
        }
    }

    @AfterEach
    fun tearDown() {
        wrappedDs.let { }; explainDs.close(); rawDs.close()
    }

    /** Вспомогалка: найти первый узел с типом из множества */
    private fun findNode(root: PlanNode, types: Set<String>): PlanNode? {
        fun dfs(n: PlanNode): PlanNode? {
            if (n.nodeType in types) return n
            n.plans.forEach { child -> dfs(child)?.let { return it } }
            return null
        }
        return dfs(root)
    }

    @Test
    fun `seq scan vs index scan on users-age`() {
        // ---------- 1) Запрос без индекса ----------
        val ageValue = 42

        wrappedDs.connection.use { conn ->
            conn.prepareStatement("select count(*) from users where age = ?").use { ps ->
                ps.setInt(1, ageValue)
                ps.executeQuery().use { rs -> assertTrue(rs.next()); rs.getLong(1) /* прогрели */ }
            }
        }

        val capturedBefore = sinkQueue.poll(10, java.util.concurrent.TimeUnit.SECONDS)
        assertNotNull(capturedBefore, "Перехватчик не вернул BoundQuery (до индекса)")

        // без правил — сейчас проверяем план/стоимость
        val advisor: Advisor = PgAdvisor(explainDs, listOf())
        val analysisBefore = advisor.analyze(capturedBefore)
        assertTrue(analysisBefore.planCost > 0.0, "Ожидали ненулевой Total Cost до индекса")

        // ---------- 2) Создаём индекс и делаем тот же запрос ----------
        rawDs.connection.use { c ->
            c.createStatement().use { st ->
                st.execute("create index idx_users_age on users(age)")
            }
        }

        // тот же запрос
        wrappedDs.connection.use { conn ->
            conn.prepareStatement("select count(*) from users where age = ?").use { ps ->
                ps.setInt(1, ageValue)
                ps.executeQuery().use { rs -> assertTrue(rs.next()) }
            }
        }

        val capturedAfter = sinkQueue.poll(10, java.util.concurrent.TimeUnit.SECONDS)
        assertNotNull(capturedAfter, "Перехватчик не вернул BoundQuery (после индекса)")

        val analysisAfter = advisor.analyze(capturedAfter)
        assertTrue(analysisAfter.planCost > 0.0)

        println("Ожидаем падение стоимости: before=${analysisBefore.planCost}, after=${analysisAfter.planCost}")
        assertTrue(
            analysisAfter.planCost < analysisBefore.planCost,
            "Ожидали падение стоимости: before=${analysisBefore.planCost}, after=${analysisAfter.planCost}"
        )
    }
}