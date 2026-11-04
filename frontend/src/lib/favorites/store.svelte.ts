import { api } from '../api/client';
import { sessionState } from '../auth/session.svelte';
import { getFavorites } from '../listings/data';
import type { Restaurant } from '../listings/restaurants';

/**
 * Favorites store (sc-50/51/52) — the shared, authenticated heart-state.
 *
 * Module-level runes singleton: every surface (browse cards, detail page,
 * /saved) reads and writes the SAME favourited set — no per-surface fetches, no
 * drift between the heart on a card and the one on the detail page.
 *
 * Backend contract:
 *   GET    /v1/favorites          → browse-card objects (the /saved list)
 *   POST   /v1/favorites/{id}     → 204, idempotent (favourite)
 *   DELETE /v1/favorites/{id}     → 204, idempotent (unfavourite)
 * There is no per-listing "is it favourited" read, so heart state is derived
 * from the list held here. Toggling writes optimistically and reverts on
 * failure so it feels instant but never lies.
 *
 * Anonymous: holds nothing and fetches nothing (GET /v1/favorites would 401).
 */

export type FavoritesStatus = 'idle' | 'loading' | 'ready' | 'error';

class FavoritesStore {
	/** The favourited listings (browse-card model) — the /saved list source. */
	list = $state<Restaurant[]>([]);
	status = $state<FavoritesStatus>('idle');
	error = $state<string | null>(null);
	pendingIds = $state<Set<string>>(new Set());

	#accountId: string | null = null;
	#generation = 0;

	/** True while auth is re-materializing; hearts hold a neutral placeholder. */
	get pendingAuth(): boolean {
		return sessionState.restoring;
	}

	isFavorited(id: string): boolean {
		return this.list.some((f) => f.id === id);
	}

	isPending(id: string): boolean {
		return this.pendingIds.has(id);
	}

	/**
	 * Called from the root layout's effect whenever the session account changes.
	 * Clears the previous account's list immediately (never leak across a
	 * sign-out/sign-in boundary) and loads the new one.
	 */
	syncAccount(accountId: string | null): void {
		if (accountId === this.#accountId) return;
		this.#accountId = accountId;
		this.#generation += 1;
		this.pendingIds = new Set();

		if (!accountId) {
			this.list = [];
			this.status = 'idle';
			this.error = null;
			return;
		}

		this.list = [];
		this.status = 'loading';
		this.error = null;
		void this.#fetch(this.#generation);
	}

	retry(): void {
		if (!this.#accountId) return;
		this.#generation += 1;
		this.status = 'loading';
		this.error = null;
		void this.#fetch(this.#generation);
	}

	async #fetch(generation: number): Promise<void> {
		try {
			const list = await getFavorites();
			if (generation !== this.#generation) return;
			this.list = list;
			this.status = 'ready';
		} catch {
			if (generation !== this.#generation) return;
			this.status = 'error';
			this.error = "We couldn't load your saved spots.";
		}
	}

	clearError(): void {
		this.error = null;
	}

	/** Optimistically toggle a listing; reverts on failure. */
	async toggle(id: string, restaurant?: Restaurant): Promise<void> {
		if (!this.#accountId || this.pendingIds.has(id)) return;

		const before = this.list;
		const wasFavorited = before.some((f) => f.id === id);

		// Optimistic write — flip the heart and /saved list immediately.
		this.list = wasFavorited
			? before.filter((f) => f.id !== id)
			: restaurant
				? [restaurant, ...before.filter((f) => f.id !== id)]
				: before;
		this.pendingIds = new Set(this.pendingIds).add(id);

		try {
			await (wasFavorited ? api.unfavoriteListing(id) : api.favoriteListing(id));
			this.error = null;
		} catch {
			// Revert the optimistic write — never let a failed toggle lie about
			// the server state.
			this.list = before;
			this.error = wasFavorited ? "We couldn't unsave that spot." : "We couldn't save that spot.";
		} finally {
			const next = new Set(this.pendingIds);
			next.delete(id);
			this.pendingIds = next;
		}
	}
}

export const favorites = new FavoritesStore();
