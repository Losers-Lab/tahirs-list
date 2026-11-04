import { api, ApiError } from '../api/client';
import { fetchVerificationQueue, type ReviewWithListing } from './data';

export type QueueStatus = 'idle' | 'loading' | 'ready' | 'error';

export type DecisionError = { reviewId: string; message: string };

const COMMITTEE_ROLE = 'VERIFICATION_COMMITTEE';

/** Human summary of a completed decision, shown once as the success state. */
function successNotice(approved: boolean): string {
	return approved
		? 'Approved — the listing is now VERIFIED.'
		: 'Denied — the listing stays unverified.';
}

function decisionFailureMessage(error: unknown): string {
	if (error instanceof ApiError) {
		if (error.code === 'review_not_pending')
			return 'This review was already decided or is no longer pending.';
		if (error.code === 'review_not_found') return 'That review could not be found.';
		if (error.detail) return error.detail;
	}
	return "We couldn't complete that decision. Please try again.";
}

/**
 * Verification Committee queue controller (sc-73).
 *
 * Gating: `load` is a no-op unless the viewer holds the VERIFICATION_COMMITTEE
 * role, so a non-VC/anonymous screen never triggers a 403/401. Decisions lock
 * per review, remove it on success, and surface a transient notice or an inline
 * per-review error.
 */
export class VerificationQueueController {
	reviews = $state<ReviewWithListing[]>([]);
	status = $state<QueueStatus>('idle');
	notice = $state<string | null>(null);
	decisionError = $state<DecisionError | null>(null);
	decidingIds = $state<Set<string>>(new Set());

	#isCommittee = false;
	#generation = 0;

	/** Load the queue for the current session identity (call from an effect). */
	async load(accountId: string | null, role: string | null): Promise<void> {
		const isCommittee = role === COMMITTEE_ROLE;
		if (!isCommittee || !accountId) {
			this.#isCommittee = false;
			this.#generation += 1;
			this.reviews = [];
			this.status = 'idle';
			this.decidingIds = new Set();
			return;
		}

		this.#isCommittee = true;
		const generation = ++this.#generation;
		this.status = 'loading';
		try {
			const list = await fetchVerificationQueue();
			if (generation !== this.#generation) return;
			this.reviews = list;
			this.status = 'ready';
		} catch {
			if (generation !== this.#generation) return;
			this.status = 'error';
		}
	}

	isDeciding(reviewId: string): boolean {
		return this.decidingIds.has(reviewId);
	}

	clearNotice(): void {
		this.notice = null;
	}

	clearDecisionError(): void {
		this.decisionError = null;
	}

	approve(reviewId: string, reason?: string): void {
		void this.#decide(reviewId, true, () => api.approveVerificationReview(reviewId, reason));
	}

	deny(reviewId: string, reason: string): void {
		void this.#decide(reviewId, false, () => api.denyVerificationReview(reviewId, reason));
	}

	async #decide(
		reviewId: string,
		approved: boolean,
		request: () => Promise<unknown>
	): Promise<void> {
		if (!this.#isCommittee || this.decidingIds.has(reviewId)) return;

		this.notice = null;
		this.decisionError = null;
		this.decidingIds = new Set(this.decidingIds).add(reviewId);

		try {
			await request();
			this.reviews = this.reviews.filter((r) => r.reviewId !== reviewId);
			this.decisionError = null;
			this.notice = successNotice(approved);
		} catch (error) {
			this.decisionError = { reviewId, message: decisionFailureMessage(error) };
		} finally {
			const next = new Set(this.decidingIds);
			next.delete(reviewId);
			this.decidingIds = next;
		}
	}
}
