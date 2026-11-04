import { defineEnvVars } from '@sveltejs/kit/env';

/**
 * Typed environment variables (SvelteKit 3 pattern).
 *
 * `API_PROXY_TARGET` is private (server-only): it must never be importable from
 * browser code. It defaults to the local dev backend so builds and local runs
 * work without configuration.
 */
export const variables = defineEnvVars({
	API_PROXY_TARGET: {
		schema: (value) => value ?? 'http://127.0.0.1:8080'
	}
});
