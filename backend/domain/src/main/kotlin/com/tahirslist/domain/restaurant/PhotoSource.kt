package com.tahirslist.domain.restaurant

/** Where a cover photo came from (SC-231). */
enum class PhotoSourceKind {
    /** The business uploaded it through SC-205. Ours to keep, no licence to record. */
    OWNER_UPLOAD,

    /** An OSM `image` / `wikimedia_commons` tag on the matched feature. */
    OSM,

    /** A Wikimedia Commons search result. */
    WIKIMEDIA,

    /**
     * Google Places. Displayable with attribution, but the terms allow a limited
     * cache, never a permanent copy — which is why [isPermanent] is false.
     */
    GOOGLE_PLACES,
    ;

    /** Whether the bytes may be kept as an asset. */
    val isPermanent: Boolean
        get() = this == OWNER_UPLOAD || this == OSM || this == WIKIMEDIA

    /** Whether a reuse licence must be recorded — Places and owner photos are not licensed to us. */
    val requiresLicence: Boolean
        get() = this == OSM || this == WIKIMEDIA
}

/** How long the bytes may live: ours to keep, or a cache with an expiry. */
enum class PhotoStorage {
    PERMANENT,
    CACHE,
}

/**
 * The reuse terms we accept for a free-licensed photo (SC-231).
 *
 * Anything not named here is refused, including "unknown": a photo whose terms
 * we cannot state is a photo we cannot show.
 */
enum class PhotoLicence {
    CC0,
    PUBLIC_DOMAIN,
    CC_BY,
    CC_BY_SA,
    ;

    val allowsReuse: Boolean
        get() = true

    val requiresAttribution: Boolean
        get() = this == CC_BY || this == CC_BY_SA
}
