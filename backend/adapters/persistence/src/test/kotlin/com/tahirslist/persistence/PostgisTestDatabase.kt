package com.tahirslist.persistence

import com.tahirslist.persistence.db.DatabaseFactory
import com.tahirslist.persistence.db.Migrations
import com.tahirslist.persistence.db.SqlArg
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.BooleanColumnType
import org.jetbrains.exposed.v1.core.DecimalColumnType
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.LongColumnType
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.java.UUIDColumnType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.math.BigDecimal
import java.sql.Connection
import java.util.UUID

/**
 * One real PostGIS container per test spec, migrated by Flyway and reached
 * through Exposed — the TDD harness every persistence test relies on.
 *
 * Lifecycle mirrors the previous Spring-JDBC harness: `start()` in `beforeSpec`,
 * `stop()` in `afterSpec`. The container (and the Hikari pool bound to it) is
 * per spec, so specs never share state.
 */
internal class PostgisTestDatabase {

    private val container: PostgreSQLContainer<*> = PostgreSQLContainer(
        // postgis/postgis extends the official postgres image; declare it compatible
        // so Testcontainers accepts it for PostgreSQLContainer.
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    lateinit var dataSource: HikariDataSource
        private set

    lateinit var database: Database
        private set

    val jdbcUrl: String get() = container.jdbcUrl

    val username: String get() = container.username

    val password: String get() = container.password

    val isRunning: Boolean get() = container.isRunning

    /**
     * Start the container. [migrate] is `false` only for the smoke test, which
     * proves the Flyway facade itself by invoking it (and counting) explicitly.
     */
    fun start(migrate: Boolean = true) {
        container.start()
        if (migrate) {
            Migrations.migrate(container.jdbcUrl, container.username, container.password)
        }
        dataSource = DatabaseFactory.dataSource(
            jdbcUrl = container.jdbcUrl,
            user = container.username,
            password = container.password,
            maxPoolSize = 4,
        )
        database = DatabaseFactory.connect(dataSource)
    }

    fun stop() {
        if (::dataSource.isInitialized) dataSource.close()
        if (container.isRunning) container.stop()
    }
}

/**
 * Test-side stand-in for the old `JdbcTemplate` helpers, backed by Exposed.
 * Plain Kotlin values are bound with a JDBC type inferred from the value, so
 * assertions read the same as before.
 */
private fun Any?.asSqlArg(): SqlArg = when (this) {
    is String -> VarCharColumnType(512) to this
    is Int -> IntegerColumnType() to this
    is Long -> LongColumnType() to this
    is Boolean -> BooleanColumnType() to this
    is Double -> DoubleColumnType() to this
    is BigDecimal -> DecimalColumnType(20, 6) to this
    is UUID -> UUIDColumnType() to this
    null -> VarCharColumnType() to null
    else -> error("Unsupported test parameter type: ${this::class.qualifiedName}")
}

/** `SELECT`-style query returning each row as a column-label -> value map. */
internal suspend fun PostgisTestDatabase.rows(sql: String, vararg args: Any?): List<Map<String, Any?>> =
    suspendTransaction(database) {
        queryRows(sql, args.map { it.asSqlArg() }) { rs ->
            val meta = rs.metaData
            buildMap {
                for (index in 1..meta.columnCount) {
                    put(meta.getColumnLabel(index), rs.getObject(index))
                }
            }
        }
    }

/** The single row produced by [sql]; fails when the result is not exactly one row. */
internal suspend fun PostgisTestDatabase.row(sql: String, vararg args: Any?): Map<String, Any?> =
    rows(sql, *args).single()

/** `SELECT count(...)`-style scalar, returned as an [Int] for `shouldBe 30` assertions. */
internal suspend fun PostgisTestDatabase.count(sql: String, vararg args: Any?): Int =
    (rows(sql, *args).single().values.single() as Number).toInt()

/** First column of the single row as [String]. */
internal suspend fun PostgisTestDatabase.string(sql: String, vararg args: Any?): String? =
    rows(sql, *args).single().values.single() as String?

/** First column of the single row as [Boolean]. */
internal suspend fun PostgisTestDatabase.boolean(sql: String, vararg args: Any?): Boolean? =
    rows(sql, *args).single().values.single() as Boolean?

/** First column of the single row as [BigDecimal]. */
internal suspend fun PostgisTestDatabase.decimal(sql: String, vararg args: Any?): BigDecimal? =
    rows(sql, *args).single().values.single() as BigDecimal?

/** DML statement; returns the affected-row count (used where the count is asserted). */
internal suspend fun PostgisTestDatabase.execute(sql: String, vararg args: Any?): Int =
    suspendTransaction(database) {
        executeUpdate(sql, args.map { it.asSqlArg() })
    }

/**
 * Execute a multi-statement SQL script verbatim (the V7 seed re-run test).
 *
 * Exposed prepares statements through the extended query protocol, which
 * PostgreSQL refuses for multi-command strings, so — exactly like the old
 * `JdbcTemplate.execute` path — the script goes through a plain JDBC
 * `Statement` on the transaction's own connection.
 */
internal suspend fun PostgisTestDatabase.executeScript(sql: String) {
    suspendTransaction(database) {
        val jdbc = connection.connection as Connection
        jdbc.createStatement().use { it.execute(sql) }
    }
}
