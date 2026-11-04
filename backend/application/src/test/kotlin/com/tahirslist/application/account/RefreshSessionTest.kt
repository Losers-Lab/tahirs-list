package com.tahirslist.application.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.test.runTest

/**
 * Refresh Session (sc-40) — rotating hashed refresh tokens, hardened with
 * token-family reuse detection (sc-136). A token that exists only as a
 * *consumed* record (already rotated) is a reuse/theft signal: the use case
 * revokes the ENTIRE family before rejecting. Unknown, expired, or already-rotated
 * tokens are otherwise rejected with the same generic [InvalidCredentialsException].
 * Rotation stays a single atomic consume-and-rotate (sc-133 baseline preserved).
 */
class RefreshSessionTest : FunSpec({

    val tokenIssuer = mockk<TokenIssuer>()
    val refreshTokenStore = mockk<RefreshTokenStore>()
    val accountRepository = mockk<AccountRepository>()
    val clock = Clock.System

    val family = Uuid.random()

    val refresh = RefreshSession(
        tokenIssuer = tokenIssuer,
        refreshTokenStore = refreshTokenStore,
        accountRepository = accountRepository,
        clock = clock,
    )

    beforeTest { clearMocks(tokenIssuer, refreshTokenStore, accountRepository) }

    test("rotates within the family: atomically consumes the old token and stores the new one under the same family") {
        runTest {
            val account = AccountFixture.someAccount(email = "alice@example.com")
            val stored = StoredRefreshToken(account.id, family, clock.now() + 29.days)
            coEvery { refreshTokenStore.findByToken("old-refresh-token") } returns stored
            coEvery { accountRepository.findById(account.id) } returns account
            every { tokenIssuer.issue(account) } returns SessionTokens("new.access", "new-refresh-token", "Bearer", 900)
            coEvery { refreshTokenStore.consumeAndRotate("old-refresh-token", "new-refresh-token", account.id, family, any()) } returns true

            val session = refresh.execute("old-refresh-token")

            session.tokens.accessToken shouldBe "new.access"
            session.tokens.refreshToken shouldBe "new-refresh-token"
            session.account.id shouldBe account.id
            coVerify { refreshTokenStore.consumeAndRotate("old-refresh-token", "new-refresh-token", account.id, family, any()) }
            // The old flow of a bare revoke followed by a bare store must no longer happen.
            coVerify(exactly = 0) { refreshTokenStore.revoke("old-refresh-token") }
            coVerify(exactly = 0) { refreshTokenStore.store(any(), any(), any(), any()) }
        }
    }

    test("rejects when a concurrent refresh already consumed the token (loses the rotation race)") {
        runTest {
            val account = AccountFixture.someAccount(email = "bob@example.com")
            val stored = StoredRefreshToken(account.id, family, clock.now() + 29.days)
            coEvery { refreshTokenStore.findByToken("contended-token") } returns stored
            coEvery { accountRepository.findById(account.id) } returns account
            every { tokenIssuer.issue(account) } returns SessionTokens("a", "r", "Bearer", 900)
            // The gated consume reports we did NOT own the row — this caller loses.
            coEvery { refreshTokenStore.consumeAndRotate("contended-token", "r", account.id, family, any()) } returns false

            shouldThrow<InvalidCredentialsException> { refresh.execute("contended-token") }

            // Nothing was persisted or stored for the loser.
            coVerify(exactly = 0) { refreshTokenStore.store(any(), any(), any(), any()) }
        }
    }

    test("reuse of a consumed token revokes the ENTIRE family before rejecting") {
        runTest {
            // The token is no longer live but a consumed record exists for it.
            coEvery { refreshTokenStore.findByToken("stolen-consumed-token") } returns null
            coEvery { refreshTokenStore.findConsumedByToken("stolen-consumed-token") } returns
                StoredRefreshToken(Uuid.random(), family, clock.now() + 29.days)
            coEvery { refreshTokenStore.revokeFamily(family) } returns Unit

            shouldThrow<InvalidCredentialsException> { refresh.execute("stolen-consumed-token") }

            // Reuse is a theft signal: the whole family must be torn down.
            coVerify { refreshTokenStore.revokeFamily(family) }
            verify(exactly = 0) { tokenIssuer.issue(any()) }
            coVerify(exactly = 0) { refreshTokenStore.consumeAndRotate(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { refreshTokenStore.store(any(), any(), any(), any()) }
        }
    }

    test("rejects an unknown (never-issued) token without revoking any family") {
        runTest {
            coEvery { refreshTokenStore.findByToken("garbage") } returns null
            coEvery { refreshTokenStore.findConsumedByToken("garbage") } returns null

            shouldThrow<InvalidCredentialsException> { refresh.execute("garbage") }

            coVerify(exactly = 0) { refreshTokenStore.revokeFamily(any()) }
            verify(exactly = 0) { tokenIssuer.issue(any()) }
        }
    }

    test("rejects an expired refresh token and revokes it so it cannot be replayed") {
        runTest {
            val account = AccountFixture.someAccount(email = "alice@example.com")
            val expired = StoredRefreshToken(account.id, family, clock.now() - 1.seconds)
            coEvery { refreshTokenStore.findByToken("expired-token") } returns expired
            coEvery { refreshTokenStore.revoke("expired-token") } returns Unit

            shouldThrow<InvalidCredentialsException> { refresh.execute("expired-token") }

            coVerify { refreshTokenStore.revoke("expired-token") }
            verify(exactly = 0) { tokenIssuer.issue(any()) }
            coVerify(exactly = 0) { refreshTokenStore.consumeAndRotate(any(), any(), any(), any(), any()) }
            coVerify(exactly = 0) { refreshTokenStore.store(any(), any(), any(), any()) }
        }
    }

    test("re-issues from the account fetched by id (so current RBAC role is embedded)") {
        runTest {
            val account = AccountFixture.someAccount(email = "alice@example.com")
            val stored = StoredRefreshToken(account.id, family, clock.now() + 29.days)
            coEvery { refreshTokenStore.findByToken("tok") } returns stored
            coEvery { accountRepository.findById(account.id) } returns account
            every { tokenIssuer.issue(account) } returns SessionTokens("a", "r", "Bearer", 900)
            coEvery { refreshTokenStore.consumeAndRotate("tok", "r", account.id, family, any()) } returns true

            refresh.execute("tok")

            coVerify { accountRepository.findById(account.id) }
            verify { tokenIssuer.issue(account) }
        }
    }

    test("rejects the token when the owning account no longer exists") {
        runTest {
            val orphaned = StoredRefreshToken(Uuid.random(), family, clock.now() + 29.days)
            coEvery { refreshTokenStore.findByToken("orphan") } returns orphaned
            coEvery { accountRepository.findById(orphaned.accountId) } returns null

            shouldThrow<InvalidCredentialsException> { refresh.execute("orphan") }

            verify(exactly = 0) { tokenIssuer.issue(any()) }
        }
    }
})
