import { render, screen, waitFor, within } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReviewWithListing } from '#lib/reviews/data';
import { sessionState } from '#lib/auth/session.svelte';
import Page from './+page.svelte';

vi.mock('$app/state', () => ({
	page: {
		url: new URL('http://localhost/verification'),
		params: {}
	}
}));

// VerificationQueueController imports `./data`; the alias resolves to the same
// module id, so this mock intercepts the controller's read seam.
vi.mock('#lib/reviews/data', () => ({
	fetchVerificationQueue: vi.fn()
}));

import { fetchVerificationQueue } from '#lib/reviews/data';

const fetchMock = vi.mocked(fetchVerificationQueue);

function session(role: string) {
	sessionState.current = {
		accessToken: 'at',
		tokenType: 'Bearer',
		expiresAt: Date.now() + 60_000,
		accountId: 'acc-1',
		role,
		email: 'vc@tahirs.co'
	};
	sessionState.restoring = false;
}

function anonymous() {
	sessionState.current = null;
	sessionState.restoring = false;
}

function review(over: Partial<ReviewWithListing> = {}): ReviewWithListing {
	return {
		reviewId: 'r-1',
		listingId: 'l-1',
		submittedBy: 'u-1',
		state: 'AI_SUGGESTED',
		suggestedVerdict: 'APPROVE',
		suggestionConfidence: 0.97,
		suggestionReasoning: null,
		decisionOutcome: null,
		decisionReason: null,
		decidedBy: null,
		listing: {
			id: 'l-1',
			name: 'Al-Amir Grill',
			address: '1 St',
			lat: 0,
			lng: 0,
			cuisine: 'Middle Eastern',
			isHandCut: true,
			isDelivery: false
		},
		...over
	};
}

describe('VerificationPage — /verification (sc-73)', () => {
	beforeEach(() => {
		fetchMock.mockReset();
		anonymous();
	});

	it('prompts an anonymous visitor to log in (never a 401)', () => {
		render(Page);

		const main = screen.getByRole('main');
		expect(
			within(main).getByRole('heading', { name: /Verification Committee sign in/i })
		).toBeInTheDocument();
		expect(within(main).getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it('blocks an authenticated non-VC user with a no-access message (403 gate)', () => {
		session('USER');
		render(Page);

		expect(screen.getByRole('heading', { name: /No access to this queue/i })).toBeInTheDocument();
		// The queue must never be fetched/rendered for a non-VC session.
		expect(fetchMock).not.toHaveBeenCalled();
		expect(screen.queryByText('Al-Amir Grill')).not.toBeInTheDocument();
	});

	it('renders an empty state when the queue has no pending reviews', async () => {
		session('VERIFICATION_COMMITTEE');
		fetchMock.mockResolvedValue([]);
		render(Page);

		expect(
			await screen.findByRole('heading', { name: /No pending verifications/i })
		).toBeInTheDocument();
	});

	it('renders the pending reviews for a VC session', async () => {
		session('VERIFICATION_COMMITTEE');
		fetchMock.mockResolvedValue([review()]);
		render(Page);

		const link = await screen.findByRole('link', { name: 'Al-Amir Grill' });
		expect(link).toHaveAttribute('href', '/restaurants/l-1');
		expect(screen.queryByText('No pending verifications')).not.toBeInTheDocument();
	});

	it('shows an error state with a retry action when the queue fetch fails', async () => {
		session('VERIFICATION_COMMITTEE');
		fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
		fetchMock.mockResolvedValueOnce([]);
		const user = userEvent.setup();
		render(Page);

		expect(
			await screen.findByRole('heading', { name: /We couldn't load the review queue/i })
		).toBeInTheDocument();

		const button = screen.getByRole('button', { name: 'Retry' });
		await user.click(button);

		await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
		// A successful retry replaces the error surface with the (empty) queue.
		expect(
			await screen.findByRole('heading', { name: /No pending verifications/i })
		).toBeInTheDocument();
	});
});
