import type { components, operations } from './schema';

type SignupBody = operations['signup']['requestBody']['content']['application/json'];
type LoginBody = operations['login']['requestBody']['content']['application/json'];
export type CreateListingBody = operations['create']['requestBody']['content']['application/json'];
/**
 * PATCH /v1/listings/{id} edit body (sc-23/47/48). Same full-replace shape as
 * the Add Listing request plus the sc-184 `isDelivery` flag, so the owner edit
 * reuses the add-listing form. The compiled `schema.d.ts` predates both sc-23
 * and sc-184, so — exactly as `BrowseListing`/`ListingDetail` do for the read
 * DTOs — this is typed locally against the real backend payload rather than
 * blocking on a schema regeneration.
 */
export type UpdateListingBody = CreateListingBody & { isDelivery?: boolean };
export type SignupResponse = components['schemas']['SignupResponse'];
export type AuthResponse = components['schemas']['AuthResponse'];
export type ListingResponse = components['schemas']['ListingResponse'];

/**
 * GET /v1/listings browse card (sc-171, extended sc-183). The generated schema
 * (`schema.d.ts`) predates the backend's ListingReadController, so these live
 * read DTOs are typed locally against the real backend payload rather than
 * stalling on a schema regeneration. `imageSrcset` (sc-183) carries the
 * backend's pre-rendered multi-width thumbnail set so cards can source a
 * wide-enough variant for a sharp srcset; `imageThumbnailUrl` remains the ≤400px
 * backward-compatible variant. Cards never request full-res on browse.
 */
export type BrowseListing = {
	id: string;
	name: string;
	address: string;
	/**
	 * sc-187 structured address fields (read surface: ListingReadController). The
	 * backend emits city/province/postal/country on browse, search, and detail
	 * DTOs after the V20 migration; nullable → absent on pre-ingest/legacy rows
	 * and user-added listings yet to be geocoded.
	 */
	city?: string | null;
	province?: string | null;
	postal?: string | null;
	country?: string | null;
	lat: number;
	lng: number;
	cuisine: string | null;
	isHandCut: boolean | null;
	isDelivery: boolean | null;
	verificationStatus: string;
	imageThumbnailUrl: string;
	imageSrcset?: { width: number; url: string }[] | null;
	/**
	 * SC-231 photo credit. Present only when a photo exists and may be shown:
	 * a Places cache past its expiry is omitted by the backend, because its bytes
	 * are no longer servable. `photoSourceUrl` is the public source page (never a
	 * media URL carrying a key).
	 */
	photoAttribution?: string | null;
	photoLicence?: string | null;
	photoSource?: string | null;
	photoSourceUrl?: string | null;
};

/** GET /v1/listings/{id} detail payload — BrowseListing + the full-res hero. */
export type ListingDetail = BrowseListing & {
	imageUrl: string;
	/**
	 * CertificatePanel display facts for a VERIFIED listing (sc-73 read surface):
	 * certifier / reviewedOn / expiresOn are transcribed by the Verification
	 * Committee at approval; certificateUrl serves the archived cert image.
	 * Absent (undefined) for unverified listings or when no cert is recorded.
	 */
	certificate?: {
		certifier: string | null;
		reviewedOn: string;
		expiresOn: string | null;
		certificateUrl: string;
	} | null;
};

/** GET /v1/listings/{id}/manage (SC-205) — may this account change the photo? */
export type ManageResponse = { canManage: boolean };

/**
 * sc-73 Verification Committee pending review (GET /v1/verification-committee/
 * reviews). Locally typed against the backend's ReviewResponse DTO — the stored
 * openapi.json/schema.d.ts predate the sc-73 controller, exactly as BrowseListing
 * does for the listing reads. `suggestedVerdict`/`suggestionConfidence`/
 * `suggestionReasoning` describe the hosted-AI's conservative suggestion
 * (APPROVE / DENY / NEEDS_REVIEW); a human decision overrides it.
 */
export type VerificationReview = {
	reviewId: string;
	listingId: string;
	submittedBy: string;
	state: string;
	suggestedVerdict: string | null;
	suggestionConfidence: number | null;
	suggestionReasoning: string | null;
	decisionOutcome: string | null;
	decisionReason: string | null;
	decidedBy: string | null;
};

// Always same-origin: SvelteKit's hooks.server.ts proxies /v1/* to the backend,
// so no backend CORS config is needed and the HttpOnly refresh cookie stays
// first-party. The old NEXT_PUBLIC_API_BASE cross-origin escape hatch was
// deliberately NOT carried over — it weakened the cookie/CSP guarantees.
const API_BASE = '';

/**
 * sc-133 cookie contract: the refresh token is delivered only as an HttpOnly
 * cookie scoped to the auth routes, never in a JSON body. Refresh and logout
 * therefore send NO request body — the browser presents the cookie itself.
 * `credentials: "include"` ensures the refresh cookie is sent same-origin.
 */
const COOKIE_REQUEST = { credentials: 'include' as const };

/**
 * Access-token holder for authenticated API calls (sc-138). The session store
 * (`auth/session.ts`) feeds the current in-memory access token here on every
 * auth change; `request()` emits it as a Bearer Authorization header when
 * present. Kept in the client (not the session module) so the dependency stays
 * one-way (session → client) with no import cycle. Memory-only by contract —
 * never persisted, never written to storage.
 */
let accessToken: string | null = null;

/** Set the access token used for authenticated calls (pass null to clear it). */
export function setAccessToken(token: string | null): void {
	accessToken = token;
}

/** One of the machine-readable error codes the backend returns. */
export type ApiErrorCode =
	| 'invalid_input'
	| 'email_already_exists'
	| 'weak_password'
	| 'invalid_credentials'
	| 'owner_not_found'
	| 'not_found'
	| 'internal_error'
	// sc-23 owner listing edit: a listing may only be edited by its owner.
	| 'not_listing_owner'
	// sc-73 verification-committee surface.
	| 'forbidden'
	| 'review_not_found'
	| 'review_not_pending'
	// SC-205 photo upload surface.
	| 'payload_too_large'
	| 'unauthorized'
	| 'listing_not_found';

/**
 * Typed error thrown for any non-2xx API response. `code` is the backend's
 * ErrorResponse.code, which drives which UI error treatment to render.
 */
export class ApiError extends Error {
	readonly status: number;
	readonly code: ApiErrorCode;
	readonly detail?: string;

	constructor(status: number, code: string, detail?: string) {
		super(code);
		this.name = 'ApiError';
		this.status = status;
		this.code = code as ApiErrorCode;
		this.detail = detail;
	}
}

async function request<T>(
	path: string,
	body: unknown,
	options: {
		credentials?: RequestCredentials;
		signal?: AbortSignal;
		/**
		 * HTTP method for the request. Defaults to POST (the historical default for
		 * this client). The listing READ endpoints (sc-171) pass "GET".
		 */
		method?: string;
		/**
		 * Attach the Bearer Authorization header when an access token is set.
		 * Defaults to true for protected resource calls; set to false for the
		 * cookie-authenticated auth routes so a stale bearer can never short-
		 * circuit refresh/logout before the controller reads the refresh cookie
		 * (sc-138 / sc-133 recovery contract).
		 */
		attachAuth?: boolean;
	} = {}
): Promise<T> {
	let response: Response;
	try {
		const headers: Record<string, string> = {};
		if (body !== undefined) headers['Content-Type'] = 'application/json';
		const shouldAttachAuth = options.attachAuth === false ? false : true;
		if (accessToken && shouldAttachAuth) headers.Authorization = `Bearer ${accessToken}`;
		response = await fetch(`${API_BASE}${path}`, {
			method: options.method ?? 'POST',
			headers,
			credentials: options.credentials,
			body: body !== undefined ? JSON.stringify(body) : undefined,
			signal: options.signal
		});
	} catch (error) {
		// Network / CORS / aborted — never a server response.
		if (options.signal?.aborted) throw error;
		throw new Error('network_error', { cause: error });
	}

	const text = await response.text();

	// Defect 5a guard: a non-JSON body (a reverse-proxy 502 HTML page, malformed
	// or whitespace-padded JSON) must never leak a raw SyntaxError. Normalize it
	// to a typed error instead. An unexpected 2xx with an unparseable body is a
	// network-layer failure; an unparseable error body keeps the ApiError
	// contract so the UI can still render a typed, decorated error.
	// The catch block always throws, so `data` is definitely assigned afterwards.
	let data: unknown;
	try {
		data = text ? (JSON.parse(text) as unknown) : null;
	} catch {
		if (response.ok) {
			throw new Error('network_error');
		}
		throw new ApiError(response.status, 'invalid_input');
	}

	if (!response.ok) {
		const body = (data ?? null) as { code?: unknown; message?: unknown } | null;
		const code = typeof body?.code === 'string' ? body.code : 'invalid_input';
		const detail = typeof body?.message === 'string' ? body.message : undefined;
		throw new ApiError(response.status, code, detail);
	}

	return (data ?? undefined) as T;
}

/**
 * Send one file as a multipart body (SC-205). Kept apart from [request] because
 * that helper JSON-encodes and sets a Content-Type header, which would clobber
 * the browser-generated multipart boundary.
 */
async function uploadMultipart<T>(
	path: string,
	file: File,
	field: string,
	signal?: AbortSignal
): Promise<T> {
	const form = new FormData();
	form.append(field, file);
	const headers: Record<string, string> = {};
	if (accessToken) headers.Authorization = `Bearer ${accessToken}`;

	let response: Response;
	try {
		response = await fetch(`${API_BASE}${path}`, {
			method: 'PUT',
			headers,
			credentials: 'include',
			body: form,
			signal
		});
	} catch (error) {
		if (signal?.aborted) throw error;
		throw new Error('network_error', { cause: error });
	}

	const text = await response.text();
	let data: unknown = null;
	if (text) {
		try {
			data = JSON.parse(text) as unknown;
		} catch {
			data = null;
		}
	}

	if (!response.ok) {
		const body = (data ?? null) as { code?: unknown; message?: unknown } | null;
		throw new ApiError(
			response.status,
			typeof body?.code === 'string' ? body.code : 'invalid_input',
			typeof body?.message === 'string' ? body.message : undefined
		);
	}

	return (data ?? undefined) as T;
}

export const api = {
	/**
	 * POST /v1/auth/signup — create an account. `attachAuth: false`: a stale
	 * in-memory bearer must never be presented to the public auth routes, or
	 * Spring rejects the request with 401 before the permitAll rule applies.
	 */
	signup: (body: SignupBody, signal?: AbortSignal): Promise<SignupResponse> =>
		request('/v1/auth/signup', body, { signal, attachAuth: false }),

	/** POST /v1/auth/login — authenticate (refresh is a cookie). Never attaches a stale bearer. */
	login: (body: LoginBody, signal?: AbortSignal): Promise<AuthResponse> =>
		request('/v1/auth/login', body, { signal, attachAuth: false }),

	/** POST /v1/auth/refresh — rotate via the HttpOnly refresh cookie (no body). */
	refresh: (signal?: AbortSignal): Promise<AuthResponse> =>
		request('/v1/auth/refresh', undefined, {
			...COOKIE_REQUEST,
			signal,
			// Cookie-authenticated: never present the (possibly stale) bearer.
			attachAuth: false
		}),

	/** POST /v1/auth/logout — revoke the refresh cookie (no body). Resolves on 204. */
	logout: (signal?: AbortSignal): Promise<undefined> =>
		request('/v1/auth/logout', undefined, {
			...COOKIE_REQUEST,
			signal,
			// Cookie-authenticated: never present the (possibly stale) bearer.
			attachAuth: false
		}),

	/**
	 * POST /v1/listings — add a restaurant listing (sc-138). Requires the
	 * authenticated account (the access JWT is presented via the auth surface);
	 * a listing is always created UNVERIFIED. The client supplies coordinates
	 * directly — this endpoint does not geocode.
	 */
	createListing: (
		// The live backend accepts the sc-184 isDelivery flag on create too, even
		// though the committed generated schema predates it (same drift the update
		// body is typed around).
		body: UpdateListingBody,
		signal?: AbortSignal
	): Promise<ListingResponse> => request('/v1/listings', body, { signal }),

	/**
	 * PATCH /v1/listings/{id} — edit an owned restaurant listing (sc-23/47/48).
	 * A FULL replace of the listing's editable content fields (reuses the Add
	 * Listing request shape); identity/governance fields (owner, verification
	 * status, price, rating) are preserved server-side. Requires the
	 * authenticated owner's access JWT: 403 `not_listing_owner` when the account
	 * does not own the listing, 404 `listing_not_found` when it is missing.
	 */
	updateListing: (
		id: string,
		body: UpdateListingBody,
		signal?: AbortSignal
	): Promise<ListingResponse> =>
		request(`/v1/listings/${encodeURIComponent(id)}`, body, {
			method: 'PATCH',
			signal
		}),

	/**
	 * GET /v1/listings — browse/search cards (sc-171). Public read surface; the
	 * backend returns minimal cards (thumbnail URL only, never full-res). No auth.
	 */
	getListings: (signal?: AbortSignal): Promise<BrowseListing[]> =>
		request('/v1/listings', undefined, { method: 'GET', signal }),

	/**
	 * GET /v1/listings/{id} — detail payload incl. the full-res hero `imageUrl`.
	 * Throws ApiError with status 404 when the listing does not exist.
	 */
	getListing: (id: string, signal?: AbortSignal): Promise<ListingDetail> =>
		request(`/v1/listings/${encodeURIComponent(id)}`, undefined, {
			method: 'GET',
			signal
		}),

	/**
	 * GET /v1/listings/{id}/manage — may the signed-in account add or replace this
	 * listing's photo (SC-205)? Answers `owner || ADMIN` server-side and returns
	 * only a boolean. 401 anonymous, 404 unknown listing.
	 */
	getManage: (id: string, signal?: AbortSignal): Promise<ManageResponse> =>
		request(`/v1/listings/${encodeURIComponent(id)}/manage`, undefined, {
			method: 'GET',
			signal
		}),

	/**
	 * PUT /v1/listings/{id}/image — add or replace the hero photo (sc-53, SC-205).
	 *
	 * Sent as `multipart/form-data` with one part named `image`. The browser sets
	 * the multipart boundary, so this call must NOT set a Content-Type header;
	 * the backend derives the stored media type from the bytes anyway.
	 * 204 on success, 400 undecodable image, 401 anonymous, 403 not owner/admin,
	 * 404 unknown listing, 413 over 8 MiB.
	 */
	uploadListingImage: (id: string, file: File, signal?: AbortSignal): Promise<void> =>
		uploadMultipart(`/v1/listings/${encodeURIComponent(id)}/image`, file, 'image', signal),

	/**
	 * GET /v1/favorites — the authenticated user's favourited listings as
	 * browse-card objects (same shape as GET /v1/listings). 401 when anonymous.
	 */
	getFavorites: (signal?: AbortSignal): Promise<BrowseListing[]> =>
		request('/v1/favorites', undefined, { method: 'GET', signal }),

	/**
	 * POST /v1/favorites/{listingId} — favourite a listing (sc-50). Idempotent;
	 * resolves on 204 (no body). 401 anonymous, 404 if the listing is missing.
	 */
	favoriteListing: (id: string, signal?: AbortSignal): Promise<undefined> =>
		request(`/v1/favorites/${encodeURIComponent(id)}`, undefined, {
			method: 'POST',
			signal
		}),

	/**
	 * DELETE /v1/favorites/{listingId} — unfavourite (sc-51). Idempotent;
	 * resolves on 204 (no body). 401 anonymous, 404 if the listing is missing.
	 */
	unfavoriteListing: (id: string, signal?: AbortSignal): Promise<undefined> =>
		request(`/v1/favorites/${encodeURIComponent(id)}`, undefined, {
			method: 'DELETE',
			signal
		}),

	/**
	 * GET /v1/verification-committee/reviews — the VC's pending workqueue
	 * (sc-73). Every review in AI_SUGGESTED. Bearer-authenticated; the JWT role
	 * must be VERIFICATION_COMMITTEE (403 `forbidden` for other authenticated
	 * roles, 401 for anonymous).
	 */
	getVerificationReviews: (signal?: AbortSignal): Promise<VerificationReview[]> =>
		request('/v1/verification-committee/reviews', undefined, {
			method: 'GET',
			signal
		}),

	/**
	 * POST /v1/verification-committee/reviews/{id}/approve — approve a review,
	 * promoting the listing to VERIFIED (the only path to a verified listing).
	 * `reason` is optional. Returns the updated review. 404 review_not_found,
	 * 409 review_not_pending, 403 non-VC.
	 */
	approveVerificationReview: (
		id: string,
		reason?: string,
		signal?: AbortSignal,
		cert?: { certifier?: string; expiresOn?: string }
	): Promise<VerificationReview> =>
		request(
			`/v1/verification-committee/reviews/${encodeURIComponent(id)}/approve`,
			{
				reason: reason?.trim() || undefined,
				...(cert?.certifier ? { certifier: cert.certifier } : {}),
				...(cert?.expiresOn ? { expiresOn: cert.expiresOn } : {})
			},
			{ signal }
		),

	/**
	 * POST /v1/verification-committee/reviews/{id}/deny — deny a review; the
	 * listing stays UNVERIFIED. `reason` is required (the backend rejects a
	 * blank one with 400). Returns the updated review.
	 */
	denyVerificationReview: (
		id: string,
		reason: string,
		signal?: AbortSignal
	): Promise<VerificationReview> =>
		request(
			`/v1/verification-committee/reviews/${encodeURIComponent(id)}/deny`,
			{ reason },
			{ signal }
		)
};
