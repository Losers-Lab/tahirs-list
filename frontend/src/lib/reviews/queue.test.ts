import { waitFor } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from '#lib/api/client';
import type { ReviewWithListing } from '#lib/reviews/data';
import { VerificationQueueController } from './queue.svelte';

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
		approveVerificationReview: vi.fn(),
		denyVerificationReview: vi.fn()
	}
}));

vi.mock('#lib/reviews/data', () => ({ fetchVerificationQueue: vi.fn() }));

import { fetchVerificationQueue } from '#lib/reviews/data';

const mockedFetch = vi.mocked(fetchVerificationQueue);
const mockedApprove = vi.mocked(api.approveVerificationReview);
const mockedDeny = vi.mocked(api.denyVerificationReview);

const COMMITTEE_ROLE = 'VERIFICATION_COMMITTEE';

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
			address: '112 Atlantic Ave, Brooklyn, NY',
			lat: 40.6916,
			lng: -73.9788,
			cuisine: 'Middle Eastern',
			isHandCut: true,
			isDelivery: false
		},
		...over
	};
}

/** A promise we can resolve/reject manually (for pausing a decision in flight). */
function deferred<T>() {
	let resolve!: (value: T) => void;
	let reject!: (error: unknown) => void;
	const promise = new Promise<T>((res, rej) => {
		resolve = res;
		reject = rej;
	});
	return { promise, resolve, reject };
}

describe('VerificationQueueController (sc-73)', () => {
	let queue: VerificationQueueController;

	beforeEach(() => {
		vi.restoreAllMocks();
		mockedFetch.mockReset();
		mockedApprove.mockReset();
		mockedDeny.mockReset();
		queue = new VerificationQueueController();
	});

	it('never fetches for a non-VC session and stays idle', async () => {
		await queue.load('acc-1', 'USER');

		expect(mockedFetch).not.toHaveBeenCalled();
		expect(queue.status).toBe('idle');
		expect(queue.reviews).toHaveLength(0);
	});

	it('never fetches for an anonymous session', async () => {
		await queue.load(null, COMMITTEE_ROLE);

		expect(mockedFetch).not.toHaveBeenCalled();
		expect(queue.status).toBe('idle');
		expect(queue.reviews).toHaveLength(0);
	});

	it('fetches the queue for a VC session and exposes it', async () => {
		mockedFetch.mockResolvedValue([review()]);

		const loading = queue.load('acc-1', COMMITTEE_ROLE);
		expect(queue.status).toBe('loading');

		await loading;
		expect(queue.status).toBe('ready');
		expect(queue.reviews).toHaveLength(1);
		expect(queue.reviews[0].listing?.name).toBe('Al-Amir Grill');
	});

	it('surfaces an error status when the queue fetch fails', async () => {
		mockedFetch.mockRejectedValue(new Error('boom'));

		await queue.load('acc-1', COMMITTEE_ROLE);

		expect(queue.status).toBe('error');
	});

	it('approves: removes the review and shows a VERIFIED success notice', async () => {
		mockedFetch.mockResolvedValue([review()]);
		mockedApprove.mockResolvedValue(review());
		await queue.load('acc-1', COMMITTEE_ROLE);

		queue.approve('r-1');

		await waitFor(() => expect(queue.reviews).toHaveLength(0));
		expect(mockedApprove).toHaveBeenCalledWith('r-1', undefined);
		expect(queue.notice).toMatch(/VERIFIED/i);
	});

	it('deny: passes the reason, removes the review and shows an unverified notice', async () => {
		mockedFetch.mockResolvedValue([review()]);
		mockedDeny.mockResolvedValue(review());
		await queue.load('acc-1', COMMITTEE_ROLE);

		queue.deny('r-1', 'Cert image is expired.');

		await waitFor(() => expect(queue.reviews).toHaveLength(0));
		expect(mockedDeny).toHaveBeenCalledWith('r-1', 'Cert image is expired.');
		expect(queue.notice).toMatch(/unverified/i);
	});

	it('reports a per-review decision error on failure and keeps the review', async () => {
		mockedFetch.mockResolvedValue([review()]);
		mockedApprove.mockRejectedValue(new ApiError(409, 'review_not_pending'));
		await queue.load('acc-1', COMMITTEE_ROLE);

		queue.approve('r-1');

		await waitFor(() => expect(queue.decisionError?.reviewId).toBe('r-1'));
		expect(queue.reviews).toHaveLength(1);
		expect(queue.decisionError?.message).toMatch(/already decided/i);
	});

	it('locks a review while its decision is in flight', async () => {
		mockedFetch.mockResolvedValue([review()]);
		const pending = deferred<ReviewWithListing>();
		mockedApprove.mockReturnValue(pending.promise);
		await queue.load('acc-1', COMMITTEE_ROLE);

		queue.approve('r-1');
		expect(queue.isDeciding('r-1')).toBe(true);

		pending.resolve(review());
		await waitFor(() => expect(queue.isDeciding('r-1')).toBe(false));
	});

	it('clearNotice dismisses the success banner', async () => {
		mockedFetch.mockResolvedValue([review()]);
		mockedApprove.mockResolvedValue(review());
		await queue.load('acc-1', COMMITTEE_ROLE);

		queue.approve('r-1');
		await waitFor(() => expect(queue.notice).not.toBeNull());

		queue.clearNotice();
		expect(queue.notice).toBeNull();
	});
});
