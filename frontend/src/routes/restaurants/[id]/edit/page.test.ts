import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ListingResponse } from '#lib/api/client';
import { api, ApiError } from '#lib/api/client';
import { sessionState } from '#lib/auth/session.svelte';
import type { Restaurant } from '#lib/listings/restaurants';
import Page from './+page.svelte';

vi.mock('$app/state', () => ({
	page: {
		url: new URL('http://localhost/restaurants/l-1/edit'),
		params: { id: 'l-1' }
	}
}));

vi.mock('#lib/api/client', () => {
	class ApiError extends Error {
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
	}

	return {
		api: { updateListing: vi.fn() },
		ApiError,
		setAccessToken: vi.fn()
	};
});

vi.mock('#lib/listings/data', () => ({
	getRestaurant: vi.fn()
}));

import { getRestaurant } from '#lib/listings/data';

const mockedGetRestaurant = vi.mocked(getRestaurant);

function signedIn() {
	sessionState.current = {
		accessToken: 'at',
		tokenType: 'Bearer',
		expiresAt: Date.now() + 60_000,
		accountId: 'acc-1',
		role: 'USER',
		email: 'owner@example.com'
	};
	sessionState.restoring = false;
}

function anonymous() {
	sessionState.current = null;
	sessionState.restoring = false;
}

function restoring() {
	sessionState.current = null;
	sessionState.restoring = true;
}

/** A live detail read the edit screen prefills its form from (sc-23). */
const liveListing: Restaurant = {
	id: 'l-1',
	name: 'Al-Amir Grill',
	address: '123 Main St',
	lat: 40.7128,
	lng: -74.006,
	cuisine: 'Middle Eastern',
	// Tri-state flags: hand-cut and delivery both claimed → both checked.
	isHandCut: true,
	isDelivery: true,
	verificationStatus: 'UNVERIFIED'
};

/** Minimal POST/PATCH response shape the update returns (governance fields intact). */
function updatedResponse(overrides: Partial<ListingResponse> = {}): ListingResponse {
	return {
		id: 'l-1',
		name: 'Al-Amir Grill',
		address: '123 Main St',
		lat: 40.7128,
		lng: -74.006,
		cuisine: 'middle eastern',
		isHandCut: true,
		ownerId: 'acc-1',
		verificationStatus: 'UNVERIFIED',
		createdAt: '2026-08-30T00:00:00Z',
		...overrides
	};
}

async function loadForm(overrides: Partial<Restaurant> = {}) {
	mockedGetRestaurant.mockResolvedValue({ ...liveListing, ...overrides });
	const user = userEvent.setup();
	render(Page);
	// Wait for the live read to resolve and the form to mount.
	await screen.findByRole('button', { name: 'Save changes' });
	return user;
}

describe('EditListingPage (sc-23/47/48) — auth gate + load states', () => {
	beforeEach(() => {
		vi.mocked(api.updateListing).mockReset();
		mockedGetRestaurant.mockReset();
		anonymous();
	});

	it('prompts an unauthenticated visitor to log in (no form, link to /login)', () => {
		anonymous();
		// Hold a pending read so the page's async state update can't land outside
		// act() in a synchronous test.
		mockedGetRestaurant.mockReturnValue(new Promise<Restaurant | undefined>(() => {}));
		render(Page);
		expect(screen.getByText('You need to be signed in to edit a listing.')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Log in to continue' })).toHaveAttribute(
			'href',
			'/login'
		);
		expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
		// The read is gated on the session: anonymous visitors must not fetch.
		expect(mockedGetRestaurant).not.toHaveBeenCalled();
	});

	it('holds a neutral state while a persisted session is restoring', () => {
		restoring();
		mockedGetRestaurant.mockReturnValue(new Promise<Restaurant | undefined>(() => {}));
		render(Page);
		expect(screen.getByRole('main').firstElementChild).toHaveAttribute('aria-busy', 'true');
		expect(screen.queryByText('You need to be signed in')).not.toBeInTheDocument();
	});

	it('shows an honest not-found state when the listing read is a 404', async () => {
		signedIn();
		mockedGetRestaurant.mockResolvedValue(undefined);
		render(Page);
		expect(await screen.findByText('Listing not found')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Browse listings' })).toHaveAttribute(
			'href',
			'/search'
		);
		expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();
	});

	it('shows a load error with retry that recovers once the read succeeds', async () => {
		signedIn();
		mockedGetRestaurant.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		const user = userEvent.setup();
		render(Page);
		expect(await screen.findByText("We couldn't load this listing.")).toBeInTheDocument();
		expect(screen.queryByRole('button', { name: 'Save changes' })).not.toBeInTheDocument();

		mockedGetRestaurant.mockResolvedValue(liveListing);
		await user.click(screen.getByRole('button', { name: 'Retry' }));
		expect(await screen.findByRole('button', { name: 'Save changes' })).toBeInTheDocument();
	});
});

describe('EditListingPage (sc-23/47/48) — prefill + save wiring', () => {
	beforeEach(() => {
		vi.mocked(api.updateListing).mockReset();
		mockedGetRestaurant.mockReset();
		signedIn();
	});

	it('prefills the shared listing form from the live read (flags round-tripped)', async () => {
		await loadForm();
		expect(screen.getByLabelText('Restaurant name')).toHaveValue('Al-Amir Grill');
		expect(screen.getByLabelText('Address')).toHaveValue('123 Main St');
		expect(screen.getByLabelText('Latitude')).toHaveValue('40.7128');
		expect(screen.getByLabelText('Longitude')).toHaveValue('-74.006');
		expect(screen.getByLabelText('Cuisine')).toHaveValue('Middle Eastern');
		// Claimed flags preload checked so an unrelated edit doesn't clear them.
		expect(screen.getByRole('checkbox', { name: /Hand-cut/ })).toBeChecked();
		expect(screen.getByRole('checkbox', { name: /delivery/i })).toBeChecked();
	});

	it('only checks flags the listing actually claims (unclaimed stays unchecked)', async () => {
		await loadForm({ isHandCut: false, isDelivery: null });
		expect(screen.getByRole('checkbox', { name: /Hand-cut/ })).not.toBeChecked();
		expect(screen.getByRole('checkbox', { name: /delivery/i })).not.toBeChecked();
	});

	it('PATCHes the updated values and reflects them in the saved state', async () => {
		const updated = updatedResponse({ name: 'Al-Amir Grill 2' });
		vi.mocked(api.updateListing).mockResolvedValueOnce(updated);
		const user = await loadForm();

		await user.clear(screen.getByLabelText('Restaurant name'));
		await user.type(screen.getByLabelText('Restaurant name'), 'Al-Amir Grill 2');
		await user.click(screen.getByRole('button', { name: 'Save changes' }));

		await waitFor(() =>
			expect(api.updateListing).toHaveBeenCalledWith(
				'l-1',
				expect.objectContaining({
					name: 'Al-Amir Grill 2',
					address: '123 Main St',
					lat: 40.7128,
					lng: -74.006,
					cuisine: 'Middle Eastern',
					// Claimed flags round-trip on the full-replace PATCH.
					isHandCut: true,
					isDelivery: true
				})
			)
		);
		// UI reflects the updated data returned by the backend.
		expect(await screen.findByText('Changed saved')).toBeInTheDocument();
		expect(screen.getByText('Al-Amir Grill 2')).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'View listing' })).toHaveAttribute(
			'href',
			'/restaurants/l-1'
		);
	});

	it('shows an honest owner-gate error when the account does not own the listing (403)', async () => {
		vi.mocked(api.updateListing).mockRejectedValueOnce(new ApiError(403, 'not_listing_owner'));
		const user = await loadForm();
		await user.click(screen.getByRole('button', { name: 'Save changes' }));
		expect(await screen.findByRole('alert')).toHaveTextContent(
			'You can only edit a listing you own.'
		);
	});

	it('surfaces a 401 as a session-expired prompt', async () => {
		vi.mocked(api.updateListing).mockRejectedValueOnce(new ApiError(401, 'invalid_credentials'));
		const user = await loadForm();
		await user.click(screen.getByRole('button', { name: 'Save changes' }));
		expect(await screen.findByRole('alert')).toHaveTextContent('Your session has expired');
	});

	it('maps a save-time 404 to a removed-listing message', async () => {
		vi.mocked(api.updateListing).mockRejectedValueOnce(new ApiError(404, 'listing_not_found'));
		const user = await loadForm();
		await user.click(screen.getByRole('button', { name: 'Save changes' }));
		expect(await screen.findByRole('alert')).toHaveTextContent('This listing was not found.');
	});
});
