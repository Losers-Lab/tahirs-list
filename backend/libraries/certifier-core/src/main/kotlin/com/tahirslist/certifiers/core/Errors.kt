package com.tahirslist.certifiers.core

/** Base type for every failure a certifier library reports to its caller. */
sealed class CertifierException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/** The source could not be reached or kept failing after the configured retries. */
class SourceUnavailableException(
    val url: String,
    message: String,
    cause: Throwable? = null,
    val status: Int = 0,
) : CertifierException(message, cause)

/**
 * The source refused the request for policy reasons (robots.txt, `401`, `403`).
 * Callers must not retry with a different user-agent or otherwise bypass this.
 */
class SourceBlockedException(
    val url: String,
    message: String,
    val status: Int = 0,
) : CertifierException(message)

/**
 * The response exceeded [FetchPolicy.maxResponseBytes]. Treated as a source
 * failure, not a retryable one: a directory that streams an unbounded body is
 * not something to hammer.
 */
class SourceTooLargeException(
    val url: String,
    val limit: Int,
) : CertifierException("Response from $url exceeds the $limit-byte cap")

/** The response arrived but could not be understood. */
class SourceParseException(
    val source: String,
    message: String,
    cause: Throwable? = null,
) : CertifierException(message, cause)

/** The run tried to exceed [FetchPolicy.maxRequestsPerRun]. */
class SourceBudgetExceededException(
    val budget: Int,
) : CertifierException("Request budget of $budget exhausted for this run")
