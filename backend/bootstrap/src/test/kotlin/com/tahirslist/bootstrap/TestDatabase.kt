package com.tahirslist.bootstrap

import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.math.BigDecimal
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Test-side database access: the Exposed/JDBC replacement for the old
 * `JdbcTemplate` helpers the Spring specs used. Every call runs in its own
 * `suspendTransaction` against the shared test container, so fixtures and
 * assertions never interfere with the application's own pool.
 *
 * Plain Kotlin values are bound as JDBC objects, so call sites read exactly like
 * the previous `jdbc.queryForList(...)` / `jdbc.update(...)` calls.
 */

/** `SELECT`-style query returning each row as a column-label -> value map. */
suspend fun dbRows(sql: String, vararg args: Any?): List<Map<String, Any?>> = withJdbc { connection ->
    connection.prepareStatement(sql).use { statement ->
        statement.bindAll(args)
        statement.executeQuery().use(::readRows)
    }
}

/** The single row produced by [sql]; fails when the result is not exactly one row. */
suspend fun dbRow(sql: String, vararg args: Any?): Map<String, Any?> = dbRows(sql, *args).single()

/** `SELECT count(...)`-style scalar, as an [Int] (`shouldBe 1` assertions). */
suspend fun dbCount(sql: String, vararg args: Any?): Int = (dbValue(sql, *args) as Number).toInt()

/** First column of the single row. */
suspend fun dbValue(sql: String, vararg args: Any?): Any? = dbRow(sql, *args).values.single()

/** First column of the single row as [String]. */
suspend fun dbString(sql: String, vararg args: Any?): String? = dbValue(sql, *args) as String?

/** First column of the single row as [Boolean]. */
suspend fun dbBoolean(sql: String, vararg args: Any?): Boolean? = dbValue(sql, *args) as Boolean?

/** First column of the single row as [BigDecimal]. */
suspend fun dbDecimal(sql: String, vararg args: Any?): BigDecimal? = dbValue(sql, *args) as BigDecimal?

/** DML statement; returns the affected-row count. */
suspend fun dbUpdate(sql: String, vararg args: Any?): Int = withJdbc { connection ->
    connection.prepareStatement(sql).use { statement ->
        statement.bindAll(args)
        statement.executeUpdate()
    }
}

/**
 * Runs [block] with the raw JDBC connection of a fresh Exposed transaction.
 *
 * The connection is owned by Exposed and returned to the pool when the
 * transaction completes — callers must not close it (only the statements and
 * result sets they open).
 */
private suspend fun <T> withJdbc(block: (Connection) -> T): T =
    suspendTransaction(SharedPostgres.database) {
        block(connection.connection as Connection)
    }

private fun PreparedStatement.bindAll(args: Array<out Any?>) {
    args.forEachIndexed { index, arg ->
        // kotlin.uuid.Uuid must be converted to the JDBC-native UUID.
        setObject(index + 1, if (arg is Uuid) arg.toJavaUuid() else arg)
    }
}

private fun readRows(resultSet: ResultSet): List<Map<String, Any?>> {
    val meta = resultSet.metaData
    return buildList {
        while (resultSet.next()) {
            add(
                buildMap {
                    for (index in 1..meta.columnCount) {
                        put(meta.getColumnLabel(index), resultSet.getObject(index))
                    }
                },
            )
        }
    }
}
