package com.tahirslist.application.photo

import com.tahirslist.application.image.ImageFetchException
import com.tahirslist.application.image.ImageFetcher
import com.tahirslist.application.image.IngestHeroImage
import com.tahirslist.application.image.StoredImage
import com.tahirslist.domain.restaurant.CoverPhoto
import com.tahirslist.domain.restaurant.PhotoSourceKind
import com.tahirslist.domain.restaurant.PhotoStorage
import com.tahirslist.domain.restaurant.RestaurantListing
import java.security.MessageDigest
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException

/** What the pass would do with one listing, decided without any network call. */
data class PhotoRow(
    val listing: RestaurantListing,
    val current: CoverPhoto?,
    val plan: Plan,
) {
    enum class Plan {
        /** An owner uploaded a photo; the pass never touches it (INV-14). */
        SKIP_OWNER,

        /** A servable photo is already there and a replace was not asked for. */
        SKIP_PRESENT,

        /** The Places cache is past its expiry: refetch, same place id. */
        REFRESH_CACHE,

        /** No photo: ask the free sources, then Places. */
        SEARCH,
    }
}

/** The reviewed scope. Execute applies exactly this list; it does not re-decide it. */
data class PhotoReview(val rows: List<PhotoRow>)

/** One listing's outcome, so a batch never reports a silent success. */
sealed interface PhotoOutcome {
    val listingId: Uuid
    val listingName: String

    data class Attached(
        override val listingId: Uuid,
        override val listingName: String,
        val photo: CoverPhoto,
    ) : PhotoOutcome

    data class Skipped(
        override val listingId: Uuid,
        override val listingName: String,
        val reason: String,
    ) : PhotoOutcome

    /** Every source had nothing. A gap, not a failure (INV-15). */
    data class NoPhoto(
        override val listingId: Uuid,
        override val listingName: String,
        val reason: String,
    ) : PhotoOutcome

    /** Candidates were offered and refused — licence, credit, or a fetch that failed. */
    data class Rejected(
        override val listingId: Uuid,
        override val listingName: String,
        val reasons: List<String>,
    ) : PhotoOutcome
}

data class PhotoResult(val outcomes: List<PhotoOutcome>) {
    val attached: Int get() = outcomes.count { it is PhotoOutcome.Attached }
    val skipped: Int get() = outcomes.count { it is PhotoOutcome.Skipped }
    val missing: Int get() = outcomes.count { it is PhotoOutcome.NoPhoto }
    val rejected: Int get() = outcomes.count { it is PhotoOutcome.Rejected }

    fun summary(): String =
        "attached=$attached skipped=$skipped missing=$missing rejected=$rejected of ${outcomes.size}"
}

/**
 * UC-03: give a listing a photo it is allowed to show.
 *
 * The rules the class exists to keep:
 *
 *  - **an owner photo is never replaced** (INV-14) — checked before any source is
 *    asked, so the pass cannot even look at a listing whose owner already spoke;
 *  - **free-licensed beats Places** (L-4) — sources are asked in the order the
 *    composition root supplies, and the first accepted free candidate wins; a
 *    Places photo is only tried when every free source came up empty or was
 *    refused;
 *  - **no licence, no photo** (INV-12) — a candidate whose licence or required
 *    credit is missing is refused before its bytes are fetched;
 *  - **a Places photo is a cache** with an expiry (L-4), never a permanent copy;
 *  - **nothing stays nothing** (INV-15) — no placeholder, no generated image.
 *
 * Bytes are written first and the credit row second (UC-03 transaction boundary):
 * a crash leaves orphan bytes and no row, so a photo is never visible without its
 * credit; the next run rewrites them under the same object keys.
 */
class AttachCoverPhoto(
    private val photos: CoverPhotoRepository,
    /** Ordered: the first source that accepts a candidate wins (OSM → Commons → Places). */
    private val sources: List<PhotoCandidateSource>,
    private val fetcher: ImageFetcher,
    private val hero: IngestHeroImage,
    private val clock: Clock = Clock.System,
    private val cacheTtl: kotlin.time.Duration = PLACES_CACHE_TTL,
    /**
     * Whether bytes already exist for a listing with no credit row — the seed
     * photos ingested before SC-231. Without this the pass would happily replace a
     * seed photo it knows nothing about, which is not "no photo yet".
     */
    private val hasBytes: suspend (Uuid) -> Boolean = { false },
    /**
     * Removes a listing's stored variants. Used when a replace was asked for and
     * every candidate is refused: bytes with no credit row must not stay behind to
     * be served, so the uncredited leftovers go.
     */
    private val deleteBytes: suspend (Uuid) -> Unit = {},
) {

    /** Decide what would happen, without a network call or a write. */
    suspend fun preview(listings: List<RestaurantListing>): PhotoReview = PhotoReview(
        listings.map { listing ->
            val current = photos.find(listing.id)
            PhotoRow(listing, current, planFor(current))
        },
    )

    /**
     * Apply [review].
     *
     * @param replace when true, a servable cache is refreshed and a free photo is
     *        replaced by a better-ranked one. An owner photo is still untouched.
     */
    suspend fun execute(review: PhotoReview, replace: Boolean = false): PhotoResult =
        PhotoResult(review.rows.map { attach(it, replace) })

    private fun planFor(current: CoverPhoto?): PhotoRow.Plan = when {
        current == null -> PhotoRow.Plan.SEARCH
        current.sourceKind == PhotoSourceKind.OWNER_UPLOAD -> PhotoRow.Plan.SKIP_OWNER
        current.storageClass == PhotoStorage.CACHE -> PhotoRow.Plan.REFRESH_CACHE
        else -> PhotoRow.Plan.SKIP_PRESENT
    }

    private suspend fun attach(row: PhotoRow, replace: Boolean): PhotoOutcome {
        val id = row.listing.id
        val name = row.listing.name

        if (row.plan == PhotoRow.Plan.SKIP_OWNER) {
            return PhotoOutcome.Skipped(id, name, "owner upload is never replaced")
        }
        if (row.plan == PhotoRow.Plan.SKIP_PRESENT && !replace) {
            return PhotoOutcome.Skipped(id, name, "already has a permanent photo")
        }
        // A listing with bytes but no credit row (a pre-SC-231 seed photo) is
        // treated exactly like a present photo: known bytes are not "nothing".
        if (row.plan == PhotoRow.Plan.SEARCH && !replace && hasBytes(id)) {
            return PhotoOutcome.Skipped(id, name, "already has an image")
        }
        if (row.plan == PhotoRow.Plan.REFRESH_CACHE && !replace) {
            row.current?.takeIf { it.isServableAt(nowAsJava()) }?.let {
                return PhotoOutcome.Skipped(id, name, "the cached photo has not expired")
            }
        }

        val reasons = mutableListOf<String>()
        val candidates = candidatesFor(row)

        for (candidate in candidates) {
            refusal(candidate)?.let {
                reasons += "${candidate.sourceKind}: ${redact(it)}"
                continue
            }

            val bytes = try {
                fetcher.fetch(candidate.fetchUrl!!)
            } catch (e: ImageFetchException) {
                // The message is copied into the run report; never let a keyed URL through.
                reasons += "${candidate.sourceKind}: fetch failed — ${redact(e.message.orEmpty())}"
                continue
            }

            val photo = try {
                candidate.toCoverPhoto(
                    contentHash = sha256(bytes),
                    expiresAt = if (candidate.storageClass == PhotoStorage.CACHE) {
                        java.time.Instant.ofEpochMilli((clock.now() + cacheTtl).toEpochMilliseconds())
                    } else {
                        null
                    },
                )
            } catch (e: IllegalArgumentException) {
                reasons += "${candidate.sourceKind}: ${redact(e.message.orEmpty())}"
                continue
            }

            try {
                hero.ingest(id, StoredImage(bytes = bytes, contentType = "application/octet-stream"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Undecodable, too large, or a storage failure: nothing was credited,
                // so the row fails and keeps whatever photo it had.
                reasons += "${candidate.sourceKind}: store failed — ${redact(e.message.orEmpty())}"
                continue
            }

            photos.save(id, photo)
            return PhotoOutcome.Attached(id, name, photo)
        }

        // No candidate survived. Bytes with no credit row must not be served, so a
        // deliberate replace removes them; a default run leaves legacy seed photos
        // alone (it never gets here for a listing that has bytes).
        if (row.current == null && replace && hasBytes(id)) {
            deleteBytes(id)
            reasons += "the uncredited image already stored was removed"
        }

        return if (reasons.isEmpty()) {
            PhotoOutcome.NoPhoto(id, name, "no source offered a photo")
        } else {
            PhotoOutcome.Rejected(id, name, reasons)
        }
    }

    /**
     * Free candidates in source order, then Places. A cache refresh asks Places
     * about the place id already on file rather than searching again.
     */
    private suspend fun candidatesFor(row: PhotoRow): List<PhotoCandidate> {
        if (row.current?.sourceKind == PhotoSourceKind.GOOGLE_PLACES &&
            row.plan == PhotoRow.Plan.REFRESH_CACHE
        ) {
            return sources.mapNotNull { it.placesCandidate(row.listing, row.current.placeId) }
        }

        val free = sources.flatMap { source ->
            source.freeCandidates(row.listing)
        }
        val places = sources.mapNotNull { it.placesCandidate(row.listing) }
        return free + places
    }

    /** The licence/credit gate, checked before a byte is fetched (INV-12). */
    private fun refusal(candidate: PhotoCandidate): String? = when {
        candidate.reason != null -> candidate.reason
        candidate.fetchUrl.isNullOrBlank() -> "no fetchable url"
        candidate.sourceKind.requiresLicence && candidate.licence == null -> "licence"
        candidate.licence?.requiresAttribution == true && candidate.author.isNullOrBlank() -> "attribution"
        candidate.sourceKind == PhotoSourceKind.GOOGLE_PLACES && candidate.placeId.isNullOrBlank() -> "place id"
        else -> null
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * Strips any query string from text that reaches the report: a Places media
     * URL carries the API key there, and the report is printed and written to disk.
     */
    private fun redact(text: String): String =
        text.replace(Regex("(https://[^\\s?]+)\\?[^\\s]+"), "$1?…")

    /** The clock as the domain's `java.time.Instant` (the domain stays JDK-typed here). */
    private fun nowAsJava(): java.time.Instant =
        java.time.Instant.ofEpochMilli(clock.now().toEpochMilliseconds())

    private companion object {
        /**
         * The Places terms allow display and a limited cache, not a permanent
         * copy. Thirty days is the cache window this project accepts; after it the
         * pass refreshes and the endpoint serves nothing meanwhile.
         */
        val PLACES_CACHE_TTL: kotlin.time.Duration = 30.days
    }
}
