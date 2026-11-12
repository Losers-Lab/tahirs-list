package com.tahirslist.domain.account

/**
 * The six MVP roles (from the product personas: U, RO, VRO, VC, IC, ET).
 * `USER` is the default assigned to a newly created account.
 */
enum class Role {
    USER,
    RESTAURANT_OWNER,
    VERIFIED_RESTAURANT_OWNER,
    VERIFICATION_COMMITTEE,
    ISSUES_COMMITTEE,
    EXTENSION_TRIGGER,

    /**
     * SC-205: the operator role. An admin may manage any listing's hero photo
     * (`PhotoWritePolicy`), independent of ownership. Never granted by sign-up;
     * promotion is an operator action (D-10). Appended last so existing
     * persisted values and the JWT-contract tests are unaffected.
     */
    ADMIN,
    ;

    companion object {
        /** The role automatically granted when a new account signs up. */
        val DEFAULT: Role = USER
    }
}