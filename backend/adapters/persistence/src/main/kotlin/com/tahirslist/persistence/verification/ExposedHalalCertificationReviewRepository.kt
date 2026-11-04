package com.tahirslist.persistence.verification

import com.tahirslist.application.verification.HalalCertificationReviewRepository
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.ReviewDecision
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationOutcome
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.domain.verification.VerificationState
import com.tahirslist.persistence.db.HalalCertificationReviews
import com.tahirslist.persistence.db.bind
import com.tahirslist.persistence.db.executeUpdate
import com.tahirslist.persistence.db.queryRows
import com.tahirslist.persistence.db.toSqlTimestamp
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import java.sql.ResultSet
import java.util.UUID
import kotlin.time.toKotlinInstant
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * Exposed implementation of the [HalalCertificationReviewRepository] port
 * against the `halal_certification_reviews` table
 * (V13__create_halal_certification_reviews.sql). Deliberately boring and
 * explicit.
 *
 * [save] is an **upsert** (`INSERT ... ON CONFLICT (id) DO UPDATE`): sc-46 writes
 * a fresh row from the claim (state, AI suggestion, consent), and sc-73 advances
 * the same row through a state transition (state + human decision), so a single
 * `save` serves both paths. The whole aggregate (state, AI suggestion, human
 * decision if any, consent) lives in one row, so every read yields a consistent
 * snapshot.
 *
 * [findById] / [findByState] back the sc-73 VC decision loop and workqueue; the
 * row is mapped back into the full aggregate so a decision can be applied to it.
 * [certificateKey] (V21) rides along in the same row: it is written at claim time
 * and preserved across the state transitions (including approval), so the public
 * certificate surface can resolve the exact artifact the committee reviewed.
 */
class ExposedHalalCertificationReviewRepository(
    private val db: Database,
) : HalalCertificationReviewRepository {

    override suspend fun save(review: HalalCertificationReview): HalalCertificationReview {
        suspendTransaction(db) {
            executeUpdate(
                """
                INSERT INTO halal_certification_reviews (
                    id, listing_id, submitted_by, state,
                    suggestion_verdict, suggestion_confidence, suggestion_reasoning,
                    decision_outcome, decision_by, decision_reason, decision_at,
                    ai_consent_at, certifier, expires_on, certificate_key,
                    created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    state = EXCLUDED.state,
                    suggestion_verdict = EXCLUDED.suggestion_verdict,
                    suggestion_confidence = EXCLUDED.suggestion_confidence,
                    suggestion_reasoning = EXCLUDED.suggestion_reasoning,
                    decision_outcome = EXCLUDED.decision_outcome,
                    decision_by = EXCLUDED.decision_by,
                    decision_reason = EXCLUDED.decision_reason,
                    decision_at = EXCLUDED.decision_at,
                    ai_consent_at = EXCLUDED.ai_consent_at,
                    certifier = EXCLUDED.certifier,
                    expires_on = EXCLUDED.expires_on,
                    certificate_key = EXCLUDED.certificate_key,
                    updated_at = EXCLUDED.updated_at
                """.trimIndent(),
                listOf(
                    HalalCertificationReviews.id bind review.id,
                    HalalCertificationReviews.listingId bind review.listingId,
                    HalalCertificationReviews.submittedBy bind review.submittedBy,
                    HalalCertificationReviews.state bind review.state.name,
                    HalalCertificationReviews.suggestionVerdict bind review.suggestion?.verdict?.name,
                    HalalCertificationReviews.suggestionConfidence bind review.suggestion?.confidence,
                    HalalCertificationReviews.suggestionReasoning bind review.suggestion?.reasoning,
                    HalalCertificationReviews.decisionOutcome bind review.decision?.outcome?.name,
                    HalalCertificationReviews.decisionBy bind review.decision?.decidedBy,
                    HalalCertificationReviews.decisionReason bind review.decision?.reason,
                    HalalCertificationReviews.decisionAt bind review.decision?.decidedAt?.toSqlTimestamp(),
                    HalalCertificationReviews.aiConsentAt bind review.aiConsentGivenAt?.toSqlTimestamp(),
                    HalalCertificationReviews.certifier bind review.certifier,
                    HalalCertificationReviews.expiresOn bind review.expiresOn?.toJavaLocalDate(),
                    HalalCertificationReviews.certificateKey bind review.certificateKey,
                    HalalCertificationReviews.createdAt bind review.createdAt.toSqlTimestamp(),
                    HalalCertificationReviews.updatedAt bind review.updatedAt.toSqlTimestamp(),
                ),
                StatementType.INSERT,
            )
        }
        return review
    }

    override suspend fun findById(id: Uuid): HalalCertificationReview? = suspendTransaction(db) {
        queryRows(
            SELECT_SQL + " WHERE r.id = ?",
            listOf(HalalCertificationReviews.id bind id),
        ) { rs -> mapReview(rs) }.firstOrNull()
    }

    override suspend fun findByState(state: VerificationState): List<HalalCertificationReview> =
        suspendTransaction(db) {
            queryRows(
                SELECT_SQL + " WHERE r.state = ?",
                listOf(HalalCertificationReviews.state bind state.name),
            ) { rs -> mapReview(rs) }
        }

    override suspend fun findLatestApprovedByListing(listingId: Uuid): HalalCertificationReview? =
        suspendTransaction(db) {
            queryRows(
                SELECT_SQL + " WHERE r.listing_id = ? AND r.state = 'APPROVED' ORDER BY r.decision_at DESC NULLS LAST, r.updated_at DESC LIMIT 1",
                listOf(HalalCertificationReviews.listingId bind listingId),
            ) { rs -> mapReview(rs) }.firstOrNull()
        }

    private fun mapReview(rs: ResultSet): HalalCertificationReview = HalalCertificationReview(
        id = rs.getObject("id", UUID::class.java).toKotlinUuid(),
        listingId = rs.getObject("listing_id", UUID::class.java).toKotlinUuid(),
        submittedBy = rs.getObject("submitted_by", UUID::class.java).toKotlinUuid(),
        state = VerificationState.valueOf(rs.getString("state")),
        createdAt = rs.getTimestamp("created_at").toInstant().toKotlinInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant().toKotlinInstant(),
        suggestion = rs.getString("suggestion_verdict")?.let {
            VerificationSuggestion(
                verdict = SuggestionVerdict.valueOf(it),
                confidence = rs.getDouble("suggestion_confidence"),
                reasoning = rs.getString("suggestion_reasoning"),
            )
        },
        decision = rs.getString("decision_outcome")?.let {
            ReviewDecision(
                outcome = VerificationOutcome.valueOf(it),
                decidedBy = rs.getObject("decision_by", UUID::class.java).toKotlinUuid(),
                reason = rs.getString("decision_reason"),
                decidedAt = rs.getTimestamp("decision_at").toInstant().toKotlinInstant(),
            )
        },
        aiConsentGivenAt = rs.getTimestamp("ai_consent_at")?.toInstant()?.toKotlinInstant(),
        certifier = rs.getString("certifier"),
        expiresOn = rs.getDate("expires_on")?.toLocalDate()?.toKotlinLocalDate(),
        certificateKey = rs.getString("certificate_key"),
    )

    private companion object {
        const val SELECT_SQL: String =
            """
            SELECT
                id, listing_id, submitted_by, state,
                suggestion_verdict, suggestion_confidence, suggestion_reasoning,
                decision_outcome, decision_by, decision_reason, decision_at,
                ai_consent_at, certifier, expires_on, certificate_key, created_at, updated_at
            FROM halal_certification_reviews r
            """
    }
}
