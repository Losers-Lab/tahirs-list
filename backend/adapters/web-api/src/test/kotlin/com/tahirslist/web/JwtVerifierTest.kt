package com.tahirslist.web

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date

/**
 * RS256 access-token verification (sc-131): the claim rules the security review
 * found missing — `nbf` and the JOSE `typ` header — pinned directly against
 * [JwtVerifier], with tokens minted by Nimbus using the same key the verifier
 * trusts.
 *
 * `nbf` matters because the issuer signs every token itself: a stolen or
 * replayed token whose validity has not started yet must not be usable early.
 * The tolerance mirrors `exp` (60 s), so an issuer clock that is slightly fast
 * does not lock clients out.
 */
class JwtVerifierTest : FunSpec({

    val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA")
        .apply { initialize(2048) }
        .generateKeyPair()

    val verifier = JwtVerifier(
        JwtConfig(issuer = ISSUER, publicKey = keyPair.public as RSAPublicKey),
    )

    fun mint(
        notBeforeSecondsAhead: Long? = null,
        typ: JOSEObjectType? = JOSEObjectType.JWT,
        issuer: String = ISSUER,
    ): String {
        val now = Instant.now()
        val claims = JWTClaimsSet.Builder()
            .subject(SUBJECT)
            .issuer(issuer)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(3600)))
            .claim("role", "USER")
            .apply {
                if (notBeforeSecondsAhead != null) {
                    notBeforeTime(Date.from(now.plusSeconds(notBeforeSecondsAhead)))
                }
            }
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
        if (typ != null) header.type(typ)
        val signed = SignedJWT(header.build(), claims)
        signed.sign(RSASSASigner(keyPair.private as RSAPrivateKey))
        return signed.serialize()
    }

    test("a well-formed token with the JWT typ is accepted") {
        val principal = verifier.verify(mint()).shouldNotBeNull()

        principal.subject shouldBe SUBJECT
        principal.role shouldBe "USER"
    }

    test("a token whose nbf is in the future (outside the clock skew) is rejected") {
        verifier.verify(mint(notBeforeSecondsAhead = 300)).shouldBeNull()
    }

    test("a token whose nbf is inside the clock skew is accepted") {
        // 30 s ahead < the 60 s tolerance the issuer and verifier share.
        verifier.verify(mint(notBeforeSecondsAhead = 30)).shouldNotBeNull()
    }

    test("a token with no nbf at all is accepted (the claim is optional)") {
        verifier.verify(mint(notBeforeSecondsAhead = null)).shouldNotBeNull()
    }

    test("a token carrying a non-JWT JOSE type is rejected") {
        verifier.verify(mint(typ = JOSEObjectType("at+jwt"))).shouldBeNull()
        verifier.verify(mint(typ = null)).shouldBeNull()
    }

    test("the typ rule does not mask the other claim rules") {
        // Sanity: a correctly typed token from the wrong issuer is still rejected.
        verifier.verify(mint(issuer = "evil-issuer")).shouldBeNull()
    }
})

private const val ISSUER = "tahirs-list"
private const val SUBJECT = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e"
