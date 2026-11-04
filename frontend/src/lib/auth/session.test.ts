import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, setAccessToken } from '#lib/api/client';
import {
	refreshAccessToken,
	restore,
	sessionState,
	signIn,
	signOut
} from '#lib/auth/session.svelte';

// The session module imports the api client as `../api/client`; Vitest matches
// mocks by resolved module id, so `#lib/api/client` covers it too.
vi.mock('#lib/api/client', () => ({
	ApiError: class ApiError extends Error {
		status: number;
		code: string;
		detail?: string;
		constructor(status: number, code: string, detail?: string) {
			super(code);
			this.name = 'ApiError';
			this.status = status;
			this.code = code;
			this.detail = detail;
		}
	},
	api: {
		refresh: vi.fn(),
		logout: vi.fn()
	},
	setAccessToken: vi.fn()
}));

const auth = vi.mocked(api);
const authToken = vi.mocked(setAccessToken);

const AUTH_RESPONSE = {
	accessToken: 'at-1',
	tokenType: 'Bearer',
	expiresIn: 900, // 15 min
	accountId: 'u-1',
	role: 'USER'
};

describe('session store — sc-133 cookie lifecycle', () => {
	beforeEach(() => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2026-08-29T12:00:00Z'));
		window.localStorage.clear();

		// Module-level runes singleton persists between tests: signOut clears the
		// access token, the identity hint, and any scheduled refresh timer.
		auth.logout.mockReset().mockResolvedValue(undefined);
		signOut();
		window.localStorage.clear();
		sessionState.current = null;
		sessionState.restoring = false;

		// Drop the cleanup call history so per-test assertions stay exact.
		auth.refresh.mockReset();
		auth.logout.mockReset().mockResolvedValue(undefined);
		authToken.mockReset();
	});

	afterEach(() => {
		vi.useRealTimers();
		window.localStorage.clear();
	});

	it('signIn keeps the access token memory-only and never writes a refresh token to storage', () => {
		signIn(AUTH_RESPONSE, 'a@b.co');

		expect(sessionState.current?.accessToken).toBe('at-1');
		expect(sessionState.current?.email).toBe('a@b.co');
		expect(sessionState.current?.expiresAt).toBe(Date.now() + 900_000);

		// The persisted identity hint carries display metadata only — never a token.
		const stored = window.localStorage.getItem('hff.session.v1');
		expect(stored).toBeTruthy();
		expect(JSON.parse(stored!)).not.toHaveProperty('accessToken');
		expect(JSON.parse(stored!)).not.toHaveProperty('refreshToken');
		expect(JSON.parse(stored!)).toEqual({
			accountId: 'u-1',
			role: 'USER',
			email: 'a@b.co'
		});
	});

	it('silently refreshes via the cookie just before access-token expiry', async () => {
		auth.refresh.mockResolvedValue({
			...AUTH_RESPONSE,
			accessToken: 'at-2',
			role: 'ADMIN'
		});

		signIn(AUTH_RESPONSE, 'a@b.co');
		expect(auth.refresh).not.toHaveBeenCalled();

		// Advance past the 5s lead before expiry (900s lifetime).
		await vi.advanceTimersByTimeAsync(900_000 - 5_000);
		expect(auth.refresh).toHaveBeenCalledTimes(1);

		// Access token + role rotate in memory; identity hint updated.
		expect(sessionState.current?.accessToken).toBe('at-2');
		expect(sessionState.current?.role).toBe('ADMIN');
		const stored = JSON.parse(window.localStorage.getItem('hff.session.v1')!);
		expect(stored).toEqual({ accountId: 'u-1', role: 'ADMIN', email: 'a@b.co' });
		expect(stored).not.toHaveProperty('accessToken');
	});

	it('a 401 refresh (expired/rotated cookie) clears the session end-to-end', async () => {
		auth.refresh.mockRejectedValue(new ApiError(401, 'invalid_credentials'));

		signIn(AUTH_RESPONSE, 'a@b.co');
		await vi.advanceTimersByTimeAsync(900_000 - 5_000);

		expect(sessionState.current).toBeNull();
		expect(window.localStorage.getItem('hff.session.v1')).toBeNull();
	});

	it('a 401 refresh during boot restore clears a stale persisted session', async () => {
		auth.refresh.mockRejectedValue(new ApiError(401, 'invalid_credentials'));
		window.localStorage.setItem(
			'hff.session.v1',
			JSON.stringify({ accountId: 'u-1', role: 'USER', email: 'old@b.co' })
		);

		restore();
		await vi.advanceTimersByTimeAsync(1);

		expect(sessionState.current).toBeNull();
		expect(window.localStorage.getItem('hff.session.v1')).toBeNull();
	});

	it('a network error during refresh leaves the current memory token untouched', async () => {
		auth.refresh.mockRejectedValue(new Error('network_error'));

		signIn(AUTH_RESPONSE, 'a@b.co');
		await vi.advanceTimersByTimeAsync(900_000 - 5_000);

		expect(sessionState.current?.accessToken).toBe('at-1');
		// Identity hint stays too — nothing forced the session to end.
		expect(JSON.parse(window.localStorage.getItem('hff.session.v1')!)).toEqual({
			accountId: 'u-1',
			role: 'USER',
			email: 'a@b.co'
		});
	});

	it('signOut calls POST /v1/auth/logout and clears local state', () => {
		auth.logout.mockResolvedValue(undefined);

		signIn(AUTH_RESPONSE, 'a@b.co');
		signOut();

		expect(auth.logout).toHaveBeenCalledTimes(1);
		expect(sessionState.current).toBeNull();
		expect(window.localStorage.getItem('hff.session.v1')).toBeNull();
	});

	it('signOut clears local state even when logout fails', async () => {
		auth.logout.mockRejectedValue(new Error('network_error'));

		signIn(AUTH_RESPONSE, 'a@b.co');
		signOut();

		await vi.advanceTimersByTimeAsync(1);
		expect(sessionState.current).toBeNull();
		expect(window.localStorage.getItem('hff.session.v1')).toBeNull();
	});

	it('restore re-materializes a signed-in session from a persisted hint via the cookie', async () => {
		auth.refresh.mockResolvedValue({ ...AUTH_RESPONSE, accessToken: 'at-boot' });
		window.localStorage.setItem(
			'hff.session.v1',
			JSON.stringify({ accountId: 'u-1', role: 'USER', email: 'boot@b.co' })
		);

		restore();
		expect(sessionState.restoring).toBe(true);

		await vi.advanceTimersByTimeAsync(1);
		expect(auth.refresh).toHaveBeenCalledTimes(1);
		expect(sessionState.current?.accessToken).toBe('at-boot');
		expect(sessionState.current?.role).toBe('USER');
		expect(sessionState.restoring).toBe(false);
	});

	it('restore is a no-op when no identity hint exists', async () => {
		restore();
		await vi.advanceTimersByTimeAsync(1);

		expect(auth.refresh).not.toHaveBeenCalled();
		expect(sessionState.current).toBeNull();
		expect(sessionState.restoring).toBe(false);
	});

	it('single-flight: concurrent refresh calls share one in-flight request', async () => {
		let resolve!: (value: typeof AUTH_RESPONSE) => void;
		auth.refresh.mockReturnValue(
			new Promise((r) => {
				resolve = r;
			})
		);

		signIn(AUTH_RESPONSE, 'a@b.co');

		// Force a refresh right at expiry and issue a second overlapping call.
		await vi.advanceTimersByTimeAsync(900_000 - 5_000); // schedules + fires timer
		const p1 = refreshAccessToken();
		const p2 = refreshAccessToken();
		expect(auth.refresh).toHaveBeenCalledTimes(1);

		resolve({ ...AUTH_RESPONSE, accessToken: 'at-2' });
		await Promise.all([p1, p2]);
		expect(auth.refresh).toHaveBeenCalledTimes(1);
	});

	it('signIn feeds the access token into the api client (sc-138 injection)', () => {
		signIn(AUTH_RESPONSE, 'a@b.co');
		expect(authToken).toHaveBeenCalledWith('at-1');
	});

	it('signOut clears the access token from the api client (sc-138)', () => {
		auth.logout.mockResolvedValue(undefined);

		signIn(AUTH_RESPONSE, 'a@b.co');
		signOut();

		// Last invocation hands the client a null token so no stale bearer leaks.
		expect(authToken).toHaveBeenLastCalledWith(null);
	});

	it('a 401 refresh (expired cookie) also clears the client access token (sc-138)', async () => {
		auth.refresh.mockRejectedValue(new ApiError(401, 'invalid_credentials'));

		signIn(AUTH_RESPONSE, 'a@b.co');
		await vi.advanceTimersByTimeAsync(900_000 - 5_000);

		expect(authToken).toHaveBeenLastCalledWith(null);
	});
});
