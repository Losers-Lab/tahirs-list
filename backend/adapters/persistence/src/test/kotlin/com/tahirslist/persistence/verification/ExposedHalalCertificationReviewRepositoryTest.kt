package com.tahirslist.persistence.verification

import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationOutcome
import com.tahirslist.domain.verification.VerificationState
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.persistence.PostgisTestDatabase
import com.tahirslist.persistence.account.ExposedAccountRepository
import com.tahirslist.persistence.count
import com.tahirslist.persistence.execute
import com.tahirslist.persistence.listing.ExposedRestaurantListingRepository
import com.tahirslist.persistence.row
import com.tahirslist.persistence.rows
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import java.sql.Timestamp
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * Persistence adapter test: proves V13 adds the halal_certification_reviews table
 * and that [ExposedHalalCertificationReviewRepository] round-trips a review
 * (state, AI suggestion, and human decision) against a real PostGIS container,
 * with the aggregate's CHECK constraints and FKs enforced at the DB layer.
 */
class ExposedHalalCertificationReviewRepositoryTest : FunSpec() {

    private val postgis = PostgisTestDatabase()
    private lateinit var reviews: ExposedHalalCertificationReviewRepository
    private lateinit var accounts: ExposedAccountRepository
    private lateinit var listings: ExposedRestaurantListingRepository

    private val now = Instant.parse("2026-09-01T12:00:00Z")

    private suspend fun newAccount(): Uuid =
        accounts.save(Account.new(email = Email("review-${Uuid.random()}@example.com"), passwordHash = "argon2id\$h")).id

    private suspend fun aListing(ownerId: Uuid): Uuid =
        listings.save(
            RestaurantListing.new(
                name = "Halal Grill ${Uuid.random()}",
                address = "123 Main St",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("mediterranean"),
                isHandCut = true,
                ownerId = ownerId,
            ),
        ).id

    init {
        beforeSpec {
            postgis.start()
            reviews = ExposedHalalCertificationReviewRepository(postgis.database)
            accounts = ExposedAccountRepository(postgis.database)
            listings = ExposedRestaurantListingRepository(postgis.database)
        }
        afterSpec { postgis.stop() }

        test("V13 migration creates the halal_certification_reviews table") {
            runTest {
                postgis.count(
                    "SELECT count(*) FROM information_schema.tables WHERE table_name = 'halal_certification_reviews'",
                ) shouldBe 1
            }
        }

        test("save persists an AI_SUGGESTED review with its suggestion") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val suggested = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4, "unclear"), now)

                reviews.save(suggested)

                val row = loadRow(suggested.id)
                row.state shouldBe "AI_SUGGESTED"
                row.listingId shouldBe listing.toString()
                row.submittedBy shouldBe owner.toString()
                row.suggestionVerdict shouldBe "NEEDS_REVIEW"
                row.suggestionConfidence shouldBe 0.4
                row.suggestionReasoning shouldBe "unclear"
            }
        }

        test("save persists the AI-analysis consent timestamp (sc-120)") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val consentGivenAt = Instant.parse("2026-09-01T12:30:00Z")
                val suggested = HalalCertificationReview.create(listing, owner, now, consentGivenAt)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4), now)

                reviews.save(suggested)

                val row = loadRow(suggested.id)
                row.aiConsentAt shouldBe "2026-09-01T12:30:00Z"
            }
        }

        test("save persists a human-approved review with its decision") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val vc = newAccount()
                val approved = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
                    .beginHumanReview(now)
                    .approve(decidedBy = vc, reason = "cert matches listing", now = now)

                reviews.save(approved)

                val row = loadRow(approved.id)
                row.state shouldBe "APPROVED"
                row.decisionOutcome shouldBe "APPROVED"
                row.decisionBy shouldBe vc.toString()
                row.decisionReason shouldBe "cert matches listing"
                row.decisionAt shouldBe "2026-09-01T12:00:00Z"
            }
        }

        test("save persists a bare SUBMITTED review with null suggestion and decision") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val submitted = HalalCertificationReview.create(listing, owner, now)

                reviews.save(submitted)

                val row = loadRow(submitted.id)
                row.state shouldBe "SUBMITTED"
                row.suggestionVerdict.shouldBeNull()
                row.suggestionConfidence.shouldBeNull()
            }
        }

        test("saving a review for a non-existent listing is rejected by the FK") {
            runTest {
                val owner = newAccount()
                val phantom = Uuid.random()

                shouldThrow<ExposedSQLException> {
                    reviews.save(HalalCertificationReview.create(phantom, owner, now))
                }
            }
        }

        test("findById returns the persisted review with its full aggregate") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val vc = newAccount()
                val approved = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95, "clear cert"), now)
                    .beginHumanReview(now)
                    .approve(decidedBy = vc, reason = "cert matches", now = now)
                reviews.save(approved)

                val found = reviews.findById(approved.id)

                // ReviewDecision is a plain class (identity equality), so compare fields.
                found!!.state shouldBe VerificationState.APPROVED
                found.listingId shouldBe listing
                found.submittedBy shouldBe owner
                found.suggestion!!.verdict shouldBe SuggestionVerdict.APPROVE
                found.suggestion!!.reasoning shouldBe "clear cert"
                found.decision!!.outcome shouldBe VerificationOutcome.APPROVED
                found.decision!!.decidedBy shouldBe vc
                found.decision!!.reason shouldBe "cert matches"
            }
        }

        test("findById returns null for an unknown review") {
            runTest {
                reviews.findById(Uuid.random()) shouldBe null
            }
        }

        test("findByState returns only reviews in that state") {
            runTest {
                postgis.execute("DELETE FROM halal_certification_reviews")
                val owner = newAccount()
                val listing = aListing(owner)
                val pending = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4), now)
                val deniedReview = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4), now)
                    .beginHumanReview(now)
                    .deny(decidedBy = newAccount(), reason = "unreadable", now = now)
                // distinct review ids (a fresh create, not a transition of `pending`)
                deniedReview.id shouldNotBe pending.id
                reviews.save(pending)
                reviews.save(deniedReview)

                val pendingIds = reviews.findByState(VerificationState.AI_SUGGESTED).map { it.id }
                val deniedIds = reviews.findByState(VerificationState.DENIED).map { it.id }

                pendingIds shouldBe listOf(pending.id)
                deniedIds shouldBe listOf(deniedReview.id)
                reviews.findByState(VerificationState.APPROVED).shouldBeEmpty()
            }
        }

        test("save is an upsert: re-saving a review across a state transition updates the same row") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val vc = newAccount()
                val pending = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
                reviews.save(pending)

                val approved = pending.beginHumanReview(now).approve(decidedBy = vc, reason = "ok", now = now)
                reviews.save(approved)

                // still exactly one row, now in the decided state
                val rows = postgis.rows(
                    "SELECT count(*) FROM halal_certification_reviews WHERE id = ?",
                    pending.id.toJavaUuid(),
                )
                rows.first().values.first() shouldBe 1L
                val found = reviews.findById(pending.id)
                found!!.state shouldBe VerificationState.APPROVED
                found.decision!!.decidedBy shouldBe vc
            }
        }

        test("save persists the certifier and expiry transcribed at approval (sc-73 read surface)") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val vc = newAccount()
                val expiresOn = LocalDate(2027, 1, 12)
                val approved = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
                    .beginHumanReview(now)
                    .approve(decidedBy = vc, reason = "cert matches", now = now, certifier = "HFSAA", expiresOn = expiresOn)

                reviews.save(approved)
                val found = reviews.findById(approved.id)

                found!!.certifier shouldBe "HFSAA"
                found.expiresOn shouldBe expiresOn
            }
        }

        test("save/findById round-trips the certificate key that identifies the reviewed evidence (sc-73)") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val vc = newAccount()
                val key = "certifications/$listing/${Uuid.random()}"
                val suggested = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
                    .copy(certificateKey = key)

                reviews.save(suggested)
                reviews.findById(suggested.id)!!.certificateKey shouldBe key

                // the key survives the approval transition (approve copies the aggregate)
                val approved = suggested.beginHumanReview(now).approve(decidedBy = vc, now = now)
                reviews.save(approved)
                reviews.findById(approved.id)!!.certificateKey shouldBe key

                // no identity-tracked evidence -> NULL (fail-closed: nothing addressable)
                val bare = HalalCertificationReview.create(listing, owner, now)
                reviews.save(bare)
                reviews.findById(bare.id)!!.certificateKey.shouldBeNull()
            }
        }

        test("findLatestApprovedByListing returns the latest APPROVED review for a listing, else null") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val vc = newAccount()

                val earlier = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.9), now)
                    .beginHumanReview(now)
                    .approve(decidedBy = vc, now = now + 60.seconds, certifier = "OldCert")
                val later = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
                    .beginHumanReview(now)
                    .approve(decidedBy = vc, now = now + 120.seconds, certifier = "NewCert")
                reviews.save(earlier)
                reviews.save(later)

                val latest = reviews.findLatestApprovedByListing(listing)

                latest!!.id shouldBe later.id
                latest.certifier shouldBe "NewCert"
                reviews.findLatestApprovedByListing(Uuid.random()) shouldBe null
            }
        }

        test("findLatestApprovedByListing ignores non-APPROVED reviews (a DENIED review is not a cert)") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)
                val denied = HalalCertificationReview.create(listing, owner, now)
                    .beginAiReview(now)
                    .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4), now)
                    .beginHumanReview(now)
                    .deny(decidedBy = newAccount(), reason = "unreadable", now = now)
                reviews.save(denied)

                reviews.findLatestApprovedByListing(listing) shouldBe null
            }
        }

        test("an out-of-machine state is rejected by the CHECK constraint") {
            runTest {
                val owner = newAccount()
                val listing = aListing(owner)

                shouldThrow<ExposedSQLException> {
                    postgis.execute(
                        """
                        INSERT INTO halal_certification_reviews (id, listing_id, submitted_by, state)
                        VALUES (?, ?, ?, 'NOT_A_STATE')
                        """.trimIndent(),
                        Uuid.random().toJavaUuid(), listing.toJavaUuid(), owner.toJavaUuid(),
                    )
                }
            }
        }
    }

    private data class Row(
        val state: String,
        val listingId: String,
        val submittedBy: String,
        val suggestionVerdict: String?,
        val suggestionConfidence: Double?,
        val suggestionReasoning: String?,
        val decisionOutcome: String?,
        val decisionBy: String?,
        val decisionReason: String?,
        val decisionAt: String?,
        val aiConsentAt: String?,
    )

    private suspend fun loadRow(id: Uuid): Row {
        val row = postgis.row(
            """
            SELECT state, listing_id, submitted_by, suggestion_verdict, suggestion_confidence,
                   suggestion_reasoning, decision_outcome, decision_by, decision_reason, decision_at,
                   ai_consent_at
            FROM halal_certification_reviews
            WHERE id = ?
            """.trimIndent(),
            id.toJavaUuid(),
        )
        return Row(
            state = row["state"] as String,
            listingId = (row["listing_id"] as UUID).toString(),
            submittedBy = (row["submitted_by"] as UUID).toString(),
            suggestionVerdict = row["suggestion_verdict"] as String?,
            suggestionConfidence = (row["suggestion_confidence"] as Number?)?.toDouble(),
            suggestionReasoning = row["suggestion_reasoning"] as String?,
            decisionOutcome = row["decision_outcome"] as String?,
            decisionBy = (row["decision_by"] as UUID?)?.toString(),
            decisionReason = row["decision_reason"] as String?,
            decisionAt = (row["decision_at"] as Timestamp?)?.toInstant()?.toString(),
            aiConsentAt = (row["ai_consent_at"] as Timestamp?)?.toInstant()?.toString(),
        )
    }
}
