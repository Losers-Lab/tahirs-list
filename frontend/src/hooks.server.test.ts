import { afterEach, describe, expect, it, vi } from 'vitest';

vi.mock('$app/env', () => ({ dev: true }));
vi.mock('$app/env/private', () => ({ API_PROXY_TARGET: 'http://backend.test' }));

import { handle } from './hooks.server';

type FakeEvent = Parameters<typeof handle>[0]['event'];

function eventFor(path: string, init: RequestInit = {}, clientAddress = '203.0.113.9'): FakeEvent {
	const url = new URL(`http://localhost${path}`);
	return {
		url,
		request: new Request(url, init),
		getClientAddress: () => clientAddress
	} as unknown as FakeEvent;
}

const resolve = vi.fn(async () => new Response('<html>page</html>', { status: 200 }));

function upstreamResponse(body: string | null = '[]', status = 200): Response {
	return new Response(body, { status, headers: { 'content-type': 'application/json' } });
}

afterEach(() => {
	vi.unstubAllGlobals();
	vi.clearAllMocks();
});

describe('hooks.server — /v1 proxy trust boundary', () => {
	it('discards client forwarding metadata and asserts the real socket peer', async () => {
		const fetchMock = vi.fn(async (_input: RequestInfo | URL, _init?: RequestInit) =>
			upstreamResponse()
		);
		vi.stubGlobal('fetch', fetchMock);

		await handle({
			event: eventFor('/v1/listings', {
				headers: {
					'x-forwarded-for': '6.6.6.6',
					'x-real-ip': '6.6.6.6',
					forwarded: 'for=6.6.6.6',
					host: 'evil.example',
					connection: 'keep-alive'
				}
			}),
			resolve
		});

		const init = fetchMock.mock.calls[0][1] as RequestInit;
		const headers = new Headers(init.headers);
		expect(headers.get('x-forwarded-for')).toBe('203.0.113.9');
		expect(headers.get('x-forwarded-proto')).toBe('http');
		expect(headers.get('x-forwarded-host')).toBe('localhost');
		expect(headers.get('x-real-ip')).toBeNull();
		expect(headers.get('forwarded')).toBeNull();
		expect(headers.get('host')).toBeNull();
		expect(headers.get('connection')).toBeNull();
	});

	it('forwards multiple Set-Cookie headers separately (logout clear must survive)', async () => {
		const upstream = new Response(null, { status: 204 });
		upstream.headers.append('set-cookie', 'refresh_token=a; Path=/v1/auth; HttpOnly');
		upstream.headers.append('set-cookie', 'other=b; Path=/');

		vi.stubGlobal(
			'fetch',
			vi.fn(async () => upstream)
		);

		const response = await handle({
			event: eventFor('/v1/auth/refresh', { method: 'POST' }),
			resolve
		});

		expect(response.headers.getSetCookie()).toEqual([
			'refresh_token=a; Path=/v1/auth; HttpOnly',
			'other=b; Path=/'
		]);
	});

	it('returns a typed JSON 502 with security headers when the API is down', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn(async () => {
				throw new Error('ECONNREFUSED');
			})
		);

		const response = await handle({ event: eventFor('/v1/listings'), resolve });

		expect(response.status).toBe(502);
		expect(response.headers.get('content-type')).toContain('application/json');
		expect(response.headers.get('cache-control')).toBe('no-store');
		expect(response.headers.get('content-security-policy')).toContain("default-src 'self'");
		expect(response.headers.get('x-content-type-options')).toBe('nosniff');
		expect(await response.json()).toEqual({
			code: 'bad_gateway',
			message: 'The API is currently unavailable.'
		});
	});

	it('rejects percent-encoded path escapes instead of proxying them', async () => {
		const fetchMock = vi.fn(async () => upstreamResponse());
		vi.stubGlobal('fetch', fetchMock);

		// WHATWG URL already collapses literal/`%2e` dot segments; `%2f` survives
		// into the pathname, so the proxy must reject it rather than forward it.
		const response = await handle({ event: eventFor('/v1/foo%2f..%2fbar'), resolve });

		expect(response.status).toBe(400);
		expect(fetchMock).not.toHaveBeenCalled();
		expect(await response.json()).toEqual({
			code: 'invalid_input',
			message: 'Invalid request path.'
		});
	});

	it('does not proxy non-/v1 paths and still applies security headers', async () => {
		const fetchMock = vi.fn();
		vi.stubGlobal('fetch', fetchMock);

		const response = await handle({ event: eventFor('/search?q=x'), resolve });

		expect(fetchMock).not.toHaveBeenCalled();
		expect(resolve).toHaveBeenCalledTimes(1);
		expect(response.headers.get('x-frame-options')).toBe('DENY');
		expect(response.headers.get('strict-transport-security')).toContain('max-age=31536000');
	});
});
