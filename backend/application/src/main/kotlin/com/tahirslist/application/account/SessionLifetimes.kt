package com.tahirslist.application.account

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * Shared session lifetime policy (ratified: short access JWT; refresh ~30 days).
 */
object SessionLifetimes {
    /** Refresh tokens live ~30 days and are rotated (single-use) on each refresh. */
    val REFRESH_LIFETIME: Duration = 30.days
}
