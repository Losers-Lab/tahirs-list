package com.tahirslist.persistence.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/**
 * Framework-free database wiring: a HikariCP pool plus the Exposed [Database]
 * bound to it. No DI container, no globals — the composition root decides the
 * pool size and owns the [HikariDataSource] lifecycle (close it on shutdown).
 *
 * [Database.connect] does not open a connection eagerly; the pool hands
 * connections to Exposed per transaction.
 */
object DatabaseFactory {

    /** Pool size used when the caller does not specify one. */
    const val DEFAULT_MAX_POOL_SIZE: Int = 10

    /** Build the connection pool for [jdbcUrl]. */
    fun dataSource(
        jdbcUrl: String,
        user: String,
        password: String,
        maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    ): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            username = user
            this.password = password
            maximumPoolSize = maxPoolSize
        },
    )

    /** Bind Exposed to an existing pool. */
    fun connect(dataSource: DataSource): Database = Database.connect(
        datasource = dataSource,
        // Exposed retries a failed transaction block up to 3 times by default.
        // The Spring adapter this replaced executed every statement exactly once,
        // so pin a single attempt: a failure propagates immediately (and a unique
        // violation is never re-attempted before being translated).
        databaseConfig = DatabaseConfig { defaultMaxAttempts = 1 },
    )

    /** Build a pool for [jdbcUrl] and bind Exposed to it. */
    fun create(
        jdbcUrl: String,
        user: String,
        password: String,
        maxPoolSize: Int = DEFAULT_MAX_POOL_SIZE,
    ): Database = connect(dataSource(jdbcUrl, user, password, maxPoolSize))
}
