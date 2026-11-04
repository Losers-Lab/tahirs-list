import type { Handle } from '@sveltejs/kit/hooks';
import { dev } from '$app/env';
import { API_PROXY_TARGET } from '$app/env/private';

/**
 * Server hooks: two jobs.
 *
 * 1. `/v1/*` same-origin API proxy — parity with the previous Next.js rewrite.
 *    The whole frontend contract (CSP `connect-src 'self'`, `img-src 'self'`,
 *    the HttpOnly refresh cookie scoped to `/v1/auth`, and relative image URLs)
 *    assumes the browser only ever talks to this origin. Do not replace this
 *    with direct cross-origin fetches.
 *
 * 2. Security headers on every response — parity with the previous
 *    `next.config.ts` headers() (CSP, HSTS, nosniff, frame denial).
 *
 * `API_PROXY_TARGET` is a private (server-only) environment variable; default
 * targets the local dev backend.
 */
const PROXY_TARGET = API_PROXY_TARGET ?? 'http://127.0.0.1:8080';

// Hop-by-hop / recomputed headers that must not be forwarded verbatim.
const STRIPPED_REQUEST_HEADERS = new Set([
	'connection',
	'keep-alive',
	'proxy-authenticate',
	'proxy-authorization',
	'te',
	'trailer',
	'transfer-encoding',
	'upgrade',
	'host',
	'content-length',
	// Client-controlled forwarding metadata is never trusted. The proxy sets
	// its own X-Forwarded-* from the actual socket peer (see proxyApiRequest),
	// which is what the backend's auth rate limiter must key on.
	'forwarded',
	'x-forwarded-for',
	'x-forwarded-host',
	'x-forwarded-port',
	'x-forwarded-proto',
	'x-real-ip'
]);

const STRIPPED_RESPONSE_HEADERS = new Set([
	'content-encoding',
	'content-length',
	'transfer-encoding'
]);

function contentSecurityPolicy(isDev: boolean): string {
	return [
		"default-src 'self'",
		// SvelteKit injects a small inline hydration bootstrap; 'unsafe-inline'
		// is required until a nonce-based policy is adopted (same tradeoff as
		// the previous Next.js app, tracked as follow-up hardening).
		"script-src 'self' 'unsafe-inline'" + (isDev ? " 'unsafe-eval'" : ''),
		"style-src 'self' 'unsafe-inline'",
		"img-src 'self' data: blob:",
		"font-src 'self' data:",
		"connect-src 'self'",
		// sc-187: the detail page embeds a Google Maps oEmbed frame.
		'frame-src https://www.google.com',
		"object-src 'none'",
		"base-uri 'self'",
		"form-action 'self'",
		"frame-ancestors 'none'",
		'upgrade-insecure-requests'
	].join('; ');
}

function applySecurityHeaders(headers: Headers, isDev: boolean): void {
	headers.set('Content-Security-Policy', contentSecurityPolicy(isDev));
	headers.set('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');
	headers.set('X-Content-Type-Options', 'nosniff');
	headers.set('X-Frame-Options', 'DENY');
}

function typedError(status: number, code: string, message: string, isDev: boolean, noStore = true) {
	const headers = new Headers({ 'Content-Type': 'application/json' });
	if (noStore) headers.set('Cache-Control', 'no-store');
	applySecurityHeaders(headers, isDev);
	return new Response(JSON.stringify({ code, message }), { status, headers });
}

// Percent-encoded dot/slash/backslash segments can normalise inside the
// backend into paths outside /v1. Reject them rather than acting as a general
// path-escape into the API server.
const ENCODED_PATH_ESCAPE = /%2e|%2f|%5c/i;

async function proxyApiRequest(
	request: Request,
	url: URL,
	clientAddress: string,
	isDev: boolean
): Promise<Response> {
	const target = new URL(url.pathname + url.search, PROXY_TARGET);

	const headers = new Headers();
	for (const [key, value] of request.headers) {
		if (!STRIPPED_REQUEST_HEADERS.has(key.toLowerCase())) headers.set(key, value);
	}
	// Re-assert trustworthy forwarding metadata from the actual peer. Behind an
	// additional trusted hop, configure adapter-node's ADDRESS_HEADER/XFF_DEPTH.
	headers.set('x-forwarded-for', clientAddress);
	headers.set('x-forwarded-proto', url.protocol.replace(':', ''));
	headers.set('x-forwarded-host', url.host);

	const init: RequestInit = {
		method: request.method,
		headers,
		redirect: 'manual'
	};
	if (request.method !== 'GET' && request.method !== 'HEAD') {
		init.body = await request.arrayBuffer();
	}

	let upstream: Response;
	try {
		upstream = await fetch(target, init);
	} catch {
		// Never leak a stack trace / HTML error page: the API client expects a
		// typed JSON error envelope (see lib/api/client.ts non-JSON guard).
		return typedError(502, 'bad_gateway', 'The API is currently unavailable.', isDev);
	}

	const responseHeaders = new Headers();
	for (const [key, value] of upstream.headers) {
		if (key.toLowerCase() === 'set-cookie') continue; // handled below
		if (!STRIPPED_RESPONSE_HEADERS.has(key.toLowerCase())) responseHeaders.append(key, value);
	}
	// `Headers` combines set-cookie into one comma-joined value when iterated;
	// getSetCookie() preserves each cookie separately (logout's Max-Age=0 matters).
	for (const cookie of upstream.headers.getSetCookie()) {
		responseHeaders.append('set-cookie', cookie);
	}
	applySecurityHeaders(responseHeaders, isDev);

	return new Response(upstream.body, {
		status: upstream.status,
		statusText: upstream.statusText,
		headers: responseHeaders
	});
}

export const handle: Handle = async ({ event, resolve }) => {
	const isDev = dev;

	// Apply security headers to an error we generate ourselves.
	if (event.url.pathname.startsWith('/v1/')) {
		if (ENCODED_PATH_ESCAPE.test(event.url.pathname)) {
			return typedError(400, 'invalid_input', 'Invalid request path.', isDev);
		}
		return proxyApiRequest(event.request, event.url, event.getClientAddress(), isDev);
	}

	const response = await resolve(event);
	applySecurityHeaders(response.headers, isDev);
	return response;
};
