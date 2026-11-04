import { browser } from '$app/env';
import { api, ApiError, setAccessToken, type AuthResponse } from '../api/client';

/**
 * Session store for the sc-133 cookie contract (Svelte 5 runes port).
 *
 * Threading model:
 *  - The **access token lives in memory only** — it is never written to
 *    localStorage or any other JS-readable persistence.
 *  - The **refresh token never exists in JS at all**. It is delivered by the
 *    backend as an HttpOnly; Secure; SameSite=Lax cookie on /v1/auth/* and is
 *    presented transparently by `/v1/auth/refresh` and `/v1/auth/logout` (both
 *    send no request body).
 *  - Only a **non-secret identity hint** (accountId, role, email) is persisted
 *    in localStorage. It is display metadata, not a credential; it lets the UI
 *    render "Signed in as …" and decide to re-materialize an access token on
 *    reload without ever touching the refresh cookie.
 *
 * SSR note: module-level $state is shared by every request in the server
 * process. This module only mutates state from browser-only entry points
 * (onMount → restore/signIn/signOut/refresh), so request data can never leak
 * between SSR responses; the server-rendered snapshot is always anonymous.
 */

export type Session = {
	accessToken: string;
	tokenType: string;
	expiresAt: number; // epoch ms — derived from expiresIn at issue/refresh time
	accountId: string;
	role: string;
	email: string;
};

type IdentityHint = Pick<Session, 'accountId' | 'role' | 'email'>;

export const sessionState = $state<{ current: Session | null; restoring: boolean }>({
	current: null,
	restoring: false
});

const IDENTITY_KEY = 'hff.session.v1';
const REFRESH_LEAD_MS = 5_000; // rotate the access token 5s before it expires

// --- Memory-only access-token state (never persisted).
let accessToken: string | null = null;
let tokenType = 'Bearer';
let expiresAt = 0;

// --- Identity hint (persisted via localStorage, non-secret).
let identity: IdentityHint | null = null;

// --- Refresh scheduling / single-flight guard.
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let refreshInFlight: Promise<void> | null = null;

// A transient refresh failure (offline, backend restart) must not leave the
// session permanently expired with no timer armed; retry on a bounded cadence.
const REFRESH_RETRY_MS = 30_000;

function now(): number {
	return Date.now();
}

function publish(): void {
	sessionState.current =
		accessToken && identity
			? {
					accessToken,
					tokenType,
					expiresAt,
					accountId: identity.accountId,
					role: identity.role,
					email: identity.email
				}
			: null;
}

function readIdentityHint(): IdentityHint | null {
	if (!browser) return null;
	try {
		const raw = window.localStorage.getItem(IDENTITY_KEY);
		if (!raw) return null;
		const parsed = JSON.parse(raw) as IdentityHint;
		if (!parsed.accountId || !parsed.role) return null;
		return parsed;
	} catch {
		return null;
	}
}

function writeIdentityHint(hint: IdentityHint | null): void {
	if (!browser) return;
	try {
		if (hint) window.localStorage.setItem(IDENTITY_KEY, JSON.stringify(hint));
		else window.localStorage.removeItem(IDENTITY_KEY);
	} catch {
		// Storage unavailable (private mode / quota) — session is memory-only anyway.
	}
}

function clearRefreshTimer(): void {
	if (refreshTimer) {
		clearTimeout(refreshTimer);
		refreshTimer = null;
	}
	if (retryTimer) {
		clearTimeout(retryTimer);
		retryTimer = null;
	}
}

function scheduleRetry(): void {
	if (!browser) return;
	if (retryTimer) clearTimeout(retryTimer);
	retryTimer = setTimeout(() => {
		retryTimer = null;
		void refreshAccessToken();
	}, REFRESH_RETRY_MS);
}

/**
 * Apply a refreshed (or freshly issued) auth payload. `email` may come from an
 * existing identity hint because refresh responses do not carry the email.
 */
function applyAuth(auth: AuthResponse, emailFromHint: string | undefined): void {
	accessToken = auth.accessToken;
	setAccessToken(auth.accessToken);
	tokenType = auth.tokenType;
	expiresAt = now() + auth.expiresIn * 1000;
	const email = emailFromHint || identity?.email || '';
	identity = { accountId: auth.accountId, role: auth.role, email };
	writeIdentityHint(identity);
	sessionState.restoring = false;
	scheduleRefresh();
	publish();
}

/** Clear all local session state without calling the server. */
function clearSessionLocally(): void {
	clearRefreshTimer();
	accessToken = null;
	setAccessToken(null);
	tokenType = 'Bearer';
	expiresAt = 0;
	identity = null;
	sessionState.restoring = false;
	writeIdentityHint(null);
	publish();
}

function scheduleRefresh(): void {
	if (!browser) return;
	clearRefreshTimer();
	const delay = Math.max(0, expiresAt - now() - REFRESH_LEAD_MS);
	// Guard: extremely long-lived values fall back to the largest safe timeout.
	refreshTimer = setTimeout(() => void refreshAccessToken(), Math.min(delay, 2_147_483_647));
}

/** Silent rotation: presents the refresh cookie; never a request body. */
export async function refreshAccessToken(): Promise<void> {
	if (refreshInFlight) return refreshInFlight;
	if (!identity) return; // nothing to restore
	refreshInFlight = (async () => {
		try {
			const auth = await api.refresh();
			applyAuth(auth, identity?.email);
		} catch (error) {
			if (error instanceof ApiError && (error.status === 401 || error.status === 400)) {
				// Expired / rotated / missing refresh cookie → session is over.
				clearSessionLocally();
			} else {
				// Network/5xx errors leave the current (memory-only) state
				// untouched — but re-arm a retry so the token is not left expired
				// forever while the UI still shows a signed-in account.
				scheduleRetry();
			}
		} finally {
			refreshInFlight = null;
		}
	})();
	return refreshInFlight;
}

export function signIn(auth: AuthResponse, email: string): void {
	accessToken = auth.accessToken;
	setAccessToken(auth.accessToken);
	tokenType = auth.tokenType;
	expiresAt = now() + auth.expiresIn * 1000;
	identity = { accountId: auth.accountId, role: auth.role, email };
	writeIdentityHint(identity);
	sessionState.restoring = false;
	scheduleRefresh();
	publish();
}

/**
 * Sign out: revoke the refresh cookie server-side, then clear local state even
 * if the network call fails (local sign-out must never be blocked by logout).
 */
export function signOut(): void {
	clearSessionLocally();
	api.logout().catch(() => {
		// Best-effort server-side revocation. Local state is already cleared.
	});
}

/**
 * Called once on app boot (layout onMount). If a persisted identity hint
 * exists, re-materialize the access token via the refresh cookie; otherwise the
 * user is anonymous.
 */
export function restore(): void {
	if (!browser) return;
	const hint = readIdentityHint();
	if (!hint) return;
	identity = hint;
	sessionState.restoring = true;
	void refreshAccessToken();
}
