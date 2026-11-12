package com.tahirslist.application.ingestion

import com.tahirslist.domain.restaurant.AdmissionDecision
import com.tahirslist.domain.restaurant.CertificationClaim
import com.tahirslist.domain.restaurant.CertificationTier
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.EnrichmentSource
import com.tahirslist.domain.restaurant.HandCutDeclaration
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.VenueType
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A completed ingestion run's candidate document, as reviewed before a load. */
data class CandidateBatch(
    val runId: String,
    val generatedAt: Instant,
    val candidates: List<IngestionCandidate>,
    val rejects: List<IngestReject> = emptyList(),
) {
    init {
        require(runId.isNotBlank()) { "a candidate batch must name its run" }
    }
}

/**
 * The on-disk candidate contract (`candidates.json`).
 *
 * DTOs carry ISO strings rather than domain types so the artifact stays readable
 * and stable; the codec is the single place the two shapes meet.
 */
object CandidateBatchJson {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(batch: CandidateBatch): String = json.encodeToString(
        CandidateBatchDto(
            runId = batch.runId,
            generatedAt = batch.generatedAt.toString(),
            candidates = batch.candidates.map { it.toDto() },
            rejects = batch.rejects.map { it.toDto() },
        ),
    )

    fun decode(text: String): CandidateBatch {
        val dto = json.decodeFromString<CandidateBatchDto>(text)
        return CandidateBatch(
            runId = dto.runId,
            generatedAt = Instant.parse(dto.generatedAt),
            candidates = dto.candidates.map { it.toDomain() },
            rejects = dto.rejects.map { it.toDomain() },
        )
    }
}

@Serializable
private data class CandidateBatchDto(
    val runId: String,
    val generatedAt: String,
    val candidates: List<CandidateDto>,
    val rejects: List<RejectDto> = emptyList(),
)

@Serializable
private data class CandidateDto(
    val name: String,
    val address: String,
    val city: String? = null,
    val region: String? = null,
    val postal: String? = null,
    val country: String? = null,
    val latitude: Double,
    val longitude: Double,
    val venueKey: String,
    val metro: String? = null,
    val crossContamination: String? = null,
    /** SC-231: the gate's verdict, stored as its two names so the artifact stays readable. */
    val venueType: String? = null,
    val venueTypeSource: String? = null,
    val cuisine: String? = null,
    val cuisineSource: String? = null,
    val cuisineConflict: String? = null,
    val claims: List<ClaimDto>,
) {
    fun toDomain(): IngestionCandidate = IngestionCandidate(
        name = name,
        address = address,
        city = city,
        region = region,
        postal = postal,
        country = country,
        location = LatLng(lat = latitude, lng = longitude),
        venueKey = venueKey,
        metro = metro,
        manualCrossContamination = crossContamination
            ?.let { CrossContamination.valueOf(it) }
            ?: CrossContamination.DEFAULT,
        claims = claims.map { it.toDomain() },
        admission = venueType?.let { type ->
            AdmissionDecision.Admitted(
                venueType = VenueType.valueOf(type),
                source = EnrichmentSource.valueOf(requireNotNull(venueTypeSource) {
                    "a candidate with a venue type must name the evidence that produced it"
                }),
            )
        },
        cuisineName = cuisine,
        cuisineSource = cuisineSource?.let { EnrichmentSource.valueOf(it) },
        cuisineConflict = cuisineConflict,
    )
}

private fun IngestionCandidate.toDto(): CandidateDto = CandidateDto(
    name = name,
    address = address,
    city = city,
    region = region,
    postal = postal,
    country = country,
    latitude = location.lat,
    longitude = location.lng,
    venueKey = venueKey,
    metro = metro,
    crossContamination = manualCrossContamination.name,
    venueType = admission?.venueType?.name,
    venueTypeSource = admission?.source?.name,
    cuisine = cuisineName,
    cuisineSource = cuisineSource?.name,
    cuisineConflict = cuisineConflict,
    claims = claims.map { it.toDto() },
)

@Serializable
private data class ClaimDto(
    val source: String,
    val sourceRecordRef: String,
    val sourceUrl: String,
    val certifier: String,
    val tier: Int,
    val declaredHandCut: String,
    val expiresOn: String? = null,
    val firstSeen: String? = null,
    val lastSeen: String? = null,
) {
    fun toDomain(): CertificationClaim = CertificationClaim(
        source = source,
        sourceRecordRef = sourceRecordRef,
        sourceUrl = sourceUrl,
        certifier = certifier,
        tier = CertificationTier.entries.first { it.rank == tier },
        declaredHandCut = HandCutDeclaration.valueOf(declaredHandCut),
        expiresOn = expiresOn?.let { LocalDate.parse(it) },
        firstSeen = firstSeen?.let { Instant.parse(it) },
        lastSeen = lastSeen?.let { Instant.parse(it) },
    )
}

private fun CertificationClaim.toDto(): ClaimDto = ClaimDto(
    source = source,
    sourceRecordRef = sourceRecordRef,
    sourceUrl = sourceUrl,
    certifier = certifier,
    tier = tier.rank,
    declaredHandCut = declaredHandCut.name,
    expiresOn = expiresOn?.toString(),
    firstSeen = firstSeen?.toString(),
    lastSeen = lastSeen?.toString(),
)

@Serializable
private data class RejectDto(
    val reason: String,
    val detail: String,
    val source: String? = null,
    val sourceRecordRef: String? = null,
    val name: String? = null,
    val venueClass: String? = null,
) {
    fun toDomain(): IngestReject = IngestReject(
        reason = RejectReason.valueOf(reason),
        detail = detail,
        source = source,
        sourceRecordRef = sourceRecordRef,
        name = name,
        venueClass = venueClass,
    )
}

private fun IngestReject.toDto(): RejectDto = RejectDto(
    reason = reason.name,
    detail = detail,
    source = source,
    sourceRecordRef = sourceRecordRef,
    name = name,
    venueClass = venueClass,
)
