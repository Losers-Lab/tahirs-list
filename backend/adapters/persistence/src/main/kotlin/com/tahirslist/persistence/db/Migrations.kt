package com.tahirslist.persistence.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/**
 * Thin Kotlin facade over Flyway.
 *
 * Flyway is retained purely as the migration *executor* — there is no maintained
 * Kotlin-native equivalent, and the SQL migrations under `db/migration` are
 * unchanged. Everything else in this module talks to the database through
 * Exposed.
 */
object Migrations {

    /** Classpath location of the versioned SQL migrations (V1–V24). */
    const val LOCATIONS: String = "classpath:db/migration"

    /**
     * Apply all pending migrations to the database at [jdbcUrl].
     *
     * @return Flyway's result, including [MigrateResult.migrationsExecuted].
     */
    fun migrate(
        jdbcUrl: String,
        user: String,
        password: String,
    ): MigrateResult = Flyway.configure()
        .dataSource(jdbcUrl, user, password)
        .locations(LOCATIONS)
        .load()
        .migrate()
}
