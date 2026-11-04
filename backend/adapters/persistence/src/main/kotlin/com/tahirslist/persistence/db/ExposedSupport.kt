package com.tahirslist.persistence.db

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.IntegerColumnType
import org.jetbrains.exposed.v1.core.Transaction
import org.jetbrains.exposed.v1.core.VarCharColumnType
import org.jetbrains.exposed.v1.core.statements.Statement
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.statements.BlockingExecutable
import org.jetbrains.exposed.v1.jdbc.statements.api.JdbcPreparedStatementApi
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/**
 * Thin, explicit glue between the repositories and Exposed's JDBC transaction.
 *
 * The adapters keep their SQL verbatim (PostGIS expressions, `ON CONFLICT`
 * upserts, `INSERT ... RETURNING`) rather than re-deriving it from the DSL, so
 * two primitives are needed:
 *
 *  - [queryRows] runs a statement that *returns rows* (`SELECT`, and
 *    `INSERT ... RETURNING id`) and maps the JDBC result set. Exposed's plain
 *    `exec(String, ...)` classifies statements by their first keyword and only
 *    calls the transform when a `ResultSet` comes back, so the statement type is
 *    pinned to [StatementType.EXEC] here — that also makes
 *    `INSERT ... RETURNING` behave identically to before.
 *  - [executeUpdate] runs DML and returns the affected-row count, which several
 *    call sites use as a gate (`consumeAndRotate` exactly-one-winner,
 *    `update`/`updateVerificationStatus` null path). Exposed's string `exec`
 *    discards that count, so the statement goes through a tiny custom
 *    [BlockingExecutable] — still Exposed's prepared-statement pipeline, so
 *    parameter binding, statement logging and SQLException translation
 *    (`ExposedSQLException`) are unchanged.
 *
 * Both are `JdbcTransaction` extensions: call them inside
 * `suspendTransaction(db) { ... }`.
 */

/** A single typed bind parameter: the column type drives JDBC value conversion. */
internal typealias SqlArg = Pair<IColumnType<*>, Any?>

/** Bind [value] using [this] column's type (correct NULL handling included). */
internal infix fun Column<*>.bind(value: Any?): SqlArg = columnType to value

/** Bind a derived double (coordinates, radius) that has no dedicated column. */
internal fun doubleParam(value: Double): SqlArg = DoubleColumnType() to value

/** Bind a derived int (limit/offset) that has no dedicated column. */
internal fun intParam(value: Int): SqlArg = IntegerColumnType() to value

/** Bind a bare string (cuisine vocabulary, seed lookup values). */
internal fun stringParam(value: String): SqlArg = VarCharColumnType(512) to value

/** `kotlin.time.Instant` -> the `TIMESTAMPTZ` JDBC value (UTC offset). */
internal fun Instant.toSqlTimestamp(): OffsetDateTime = toJavaInstant().atOffset(ZoneOffset.UTC)

/**
 * Execute [sql] and map every row with [map]. Returns an empty list when the
 * driver produced no result set.
 */
internal fun <T> JdbcTransaction.queryRows(
    sql: String,
    args: Iterable<SqlArg> = emptyList(),
    map: (ResultSet) -> T,
): List<T> = exec(sql, args, StatementType.EXEC) { rs ->
    buildList {
        while (rs.next()) {
            add(map(rs))
        }
    }
} ?: emptyList()

/** Execute DML [sql] and return the number of affected rows. */
internal fun JdbcTransaction.executeUpdate(
    sql: String,
    args: Iterable<SqlArg> = emptyList(),
    statementType: StatementType = StatementType.UPDATE,
): Int = exec(AffectedRowsExecutable(sql, args.toList(), statementType)) ?: 0

/**
 * Raw DML statement whose execution result is the affected-row count. Arguments
 * are bound by [fillParameters] exactly like any other Exposed statement.
 */
private class AffectedRowsExecutable(
    private val sql: String,
    private val args: List<SqlArg>,
    type: StatementType,
) : Statement<Int>(type, emptyList()), BlockingExecutable<Int, Statement<Int>> {

    override val statement: Statement<Int> get() = this

    override fun prepareSQL(transaction: Transaction, prepared: Boolean): String = sql

    override fun arguments(): Iterable<Iterable<SqlArg>> = listOf(args)

    override fun JdbcPreparedStatementApi.executeInternal(transaction: JdbcTransaction): Int = executeUpdate()
}
