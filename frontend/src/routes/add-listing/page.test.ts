import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ListingResponse } from '#lib/api/client';
import { api, ApiError } from '#lib/api/client';
import { sessionState } from '#lib/auth/session.svelte';
import Page from './+page.svelte';

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
		api: { createListing: vi.fn() },
		ApiError,
		setAccessToken: vi.fn()
	};
});

function signedIn() {
	sessionState.current = {
		accessToken: 't',
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

const createdListing: ListingResponse = {
	id: 'l-1',
	name: 'Al-Amir Grill',
	address: '123 Main St',
	lat: 40.7128,
	lng: -74.006,
	cuisine: 'middle eastern',
	isHandCut: true,
	ownerId: 'acc-1',
	verificationStatus: 'UNVERIFIED',
	createdAt: '2026-08-30T00:00:00Z'
};

/** Fill and submit the listing form with valid values (session pre-set). */
async function fillValidForm() {
	const user = userEvent.setup();
	render(Page);
	await user.type(screen.getByLabelText('Restaurant name'), 'Al-Amir Grill');
	await user.type(screen.getByLabelText('Address'), '123 Main St');
	await user.type(screen.getByLabelText('Latitude'), '40.7128');
	await user.type(screen.getByLabelText('Longitude'), '-74.0060');
	await user.type(screen.getByLabelText('Cuisine'), 'Middle Eastern');
	await user.click(screen.getByRole('checkbox', { name: /Hand-cut/ }));
	await user.click(screen.getByRole('button', { name: 'Add listing' }));
	return user;
}

/**
 * The add screen renders the server error twice: once as its own banner and
 * once through ListingForm's `serverError` prop. Assert every alert carries the
 * same message so the test is robust to that duplication while preserving the
 * copy assertion.
 */
async function expectAlert(message: string | RegExp) {
	const alerts = await screen.findAllByRole('alert');
	expect(alerts.length).toBeGreaterThan(0);
	for (const alert of alerts) {
		expect(alert).toHaveTextContent(message);
	}
}

describe('AddListingPage (sc-138) — auth gate', () => {
	beforeEach(() => {
		vi.mocked(api.createListing).mockReset();
		anonymous();
	});

	it('prompts an unauthenticated visitor to log in (no form, link to /login)', () => {
		render(Page);

		expect(screen.getByText('You need to be signed in to add a listing.')).toBeInTheDocument();
		const link = screen.getByRole('link', { name: 'Log in to continue' });
		expect(link).toHaveAttribute('href', '/login');
		expect(screen.queryByLabelText('Restaurant name')).not.toBeInTheDocument();
	});

	it('holds a neutral state while a persisted session is restoring', () => {
		restoring();
		render(Page);
		expect(screen.getByRole('main').firstElementChild).toHaveAttribute('aria-busy', 'true');
		expect(screen.queryByText('You need to be signed in')).not.toBeInTheDocument();
	});
});

describe('AddListingPage (sc-138) — form success + data contract', () => {
	beforeEach(() => {
		vi.mocked(api.createListing).mockReset();
		signedIn();
	});

	it('posts to the backend with numeric lat/lng and shows the honest unverified success state', async () => {
		vi.mocked(api.createListing).mockResolvedValueOnce(createdListing);

		await fillValidForm();

		await waitFor(() =>
			expect(api.createListing).toHaveBeenCalledWith({
				name: 'Al-Amir Grill',
				address: '123 Main St',
				lat: 40.7128,
				lng: -74.006,
				cuisine: 'Middle Eastern',
				isHandCut: true
			})
		);

		expect(await screen.findByText('Listing saved')).toBeInTheDocument();
		expect(screen.getByText('Al-Amir Grill')).toBeInTheDocument();
		// Unverified semantics: neutral tag present, no premium "Verified" styling.
		expect(screen.getByText('Unverified')).toBeInTheDocument();
		expect(screen.queryByText('Verified')).not.toBeInTheDocument();
		// Owner-provable entry point to the manage/edit screen (sc-23/47/48):
		// the creator immediately sees an "Edit listing" affordance for the new id.
		expect(screen.getByRole('link', { name: 'Edit listing' })).toHaveAttribute(
			'href',
			'/restaurants/l-1/edit'
		);
	});

	it('logs the generic copy for a non-ApiError failure (network)', async () => {
		vi.mocked(api.createListing).mockRejectedValueOnce(new TypeError('Failed to fetch'));
		await fillValidForm();
		await expectAlert('Something went wrong. Please try again.');
	});

	it('omits isHandCut when the hand-cut box is left unchecked (recorded unknown)', async () => {
		vi.mocked(api.createListing).mockResolvedValueOnce(createdListing);

		const user = userEvent.setup();
		render(Page);
		await user.type(screen.getByLabelText('Restaurant name'), 'Al-Amir Grill');
		await user.type(screen.getByLabelText('Address'), '123 Main St');
		await user.type(screen.getByLabelText('Latitude'), '40.7128');
		await user.type(screen.getByLabelText('Longitude'), '-74.0060');
		await user.type(screen.getByLabelText('Cuisine'), 'Middle Eastern');
		await user.click(screen.getByRole('button', { name: 'Add listing' }));

		await waitFor(() =>
			expect(api.createListing).toHaveBeenCalledWith({
				name: 'Al-Amir Grill',
				address: '123 Main St',
				lat: 40.7128,
				lng: -74.006,
				cuisine: 'Middle Eastern'
			})
		);
	});

	it('disables inputs and shows the loading label while the request is in flight', async () => {
		let resolveCreate!: (value: ListingResponse) => void;
		vi.mocked(api.createListing).mockReturnValueOnce(
			new Promise<ListingResponse>((resolve) => {
				resolveCreate = resolve;
			})
		);
		await fillValidForm();

		expect(screen.getByRole('button', { name: 'Saving listing…' })).toBeDisabled();
		expect(screen.getByLabelText('Restaurant name')).toBeDisabled();

		resolveCreate(createdListing);
		await screen.findByText('Listing saved');
	});
});

describe('AddListingPage (sc-138) — validation + backend error surfacing', () => {
	beforeEach(() => {
		vi.mocked(api.createListing).mockReset();
		signedIn();
	});

	it('blocks submission client-side on blank required fields, no API call', async () => {
		const user = userEvent.setup();
		render(Page);
		await user.click(screen.getByRole('button', { name: 'Add listing' }));

		expect(await screen.findByText('Restaurant name is required.')).toBeInTheDocument();
		expect(screen.getByText('Address is required.')).toBeInTheDocument();
		expect(screen.getByText('Latitude is required.')).toBeInTheDocument();
		expect(screen.getByText('Longitude is required.')).toBeInTheDocument();
		expect(screen.getByText('Cuisine is required.')).toBeInTheDocument();

		// isHandCut is optional; only the text fields are invalid.
		expect(api.createListing).not.toHaveBeenCalled();
	});

	it('rejects an out-of-range lat client-side', async () => {
		const user = userEvent.setup();
		render(Page);
		await user.type(screen.getByLabelText('Restaurant name'), 'Al-Amir Grill');
		await user.type(screen.getByLabelText('Address'), '123 Main St');
		await user.type(screen.getByLabelText('Latitude'), '91');
		await user.type(screen.getByLabelText('Longitude'), '-74.0');
		await user.type(screen.getByLabelText('Cuisine'), 'Middle Eastern');
		await user.click(screen.getByRole('button', { name: 'Add listing' }));

		expect(await screen.findByText('Latitude must be between -90 and 90.')).toBeInTheDocument();
		expect(api.createListing).not.toHaveBeenCalled();
	});

	it('surfaces a 400 invalid_input with the backend field message (sc-134 fail-fast)', async () => {
		vi.mocked(api.createListing).mockRejectedValueOnce(
			new ApiError(400, 'invalid_input', 'name is required; address is required')
		);
		await fillValidForm();
		await expectAlert('name is required; address is required');
	});

	it('surfaces a 401 as a session-expired prompt', async () => {
		vi.mocked(api.createListing).mockRejectedValueOnce(new ApiError(401, 'invalid_credentials'));
		await fillValidForm();
		await expectAlert('Your session has expired. Please log in again to add a listing.');
	});
});
