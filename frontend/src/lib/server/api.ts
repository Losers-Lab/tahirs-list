import { API_PROXY_TARGET } from '$app/env/private';

/**
 * Server-side base URL for the Spring API, used by `+page.server.ts` loads so
 * public pages can render listing content in the SSR HTML (crawlable, per
 * ARCHITECTURE U-13). Runtime-configured; `src/env.ts` supplies the local
 * default, and a blank value makes loads fall back to the client fetch.
 */
export function apiBase(): string {
	return (API_PROXY_TARGET ?? '').replace(/\/$/, '');
}
