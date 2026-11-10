package com.tahirslist.application.listing

import com.tahirslist.domain.account.Role
import kotlin.uuid.Uuid

/**
 * SC-205: the **verified** caller of a write.
 *
 * Both fields come from the access JWT the edge already validated — [accountId]
 * is `sub`, [role] is the `role` claim. Nothing here is ever taken from a
 * request body, header or query parameter, which is what makes it safe to use
 * as the input to an authorization decision.
 *
 * The role is the token's role, not the current database row: a promotion or a
 * demotion takes effect at the next access-token refresh (INV-13, D-13). That
 * bound is deliberate — it matches the Verification Committee routes and keeps
 * the write path free of an extra read.
 */
data class Actor(
    val accountId: Uuid,
    val role: Role,
)
