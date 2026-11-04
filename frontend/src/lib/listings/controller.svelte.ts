import type { Restaurant } from './restaurants';
import { verificationStatus } from './restaurants';
import { searchListings, type BrowseFilter } from './data';

export type ListingsStatus = 'loading' | 'success' | 'error';

type SeededFor = { query: string; filter: BrowseFilter };

/**
 * Client-side listing read controller for the browse/search screens.
 *
 * Wraps the async read seam (`data.searchListings`, backed by the live
 * GET /v1/listings) so the UI exercises loading + error states while the search
 * query and chip filters drive refetches. `verifiedCount` is derived during the
 * async read (not in render) so components stay pure.
 *
 * Server loads seed the controller with the SSR list (`adopt`), so the first
 * client render reuses the server data instead of refetching; a matching
 * `load` call is skipped once. A generation counter discards stale responses
 * when the query/filter changes faster than the network resolves.
 */
export class ListingsController {
	restaurants = $state<Restaurant[]>([]);
	verifiedCount = $state(0);
	status = $state<ListingsStatus>('loading');

	#generation = 0;
	#seededFor: SeededFor | null = null;

	constructor(initial?: Restaurant[] | null, seededFor?: SeededFor) {
		if (initial) this.adopt(initial, seededFor);
	}

	/** Adopt a server-loaded list (SSR) without a client fetch. */
	adopt(list: Restaurant[], seededFor?: SeededFor): void {
		this.#generation += 1;
		this.restaurants = list;
		this.verifiedCount = list.filter((x) => verificationStatus(x) === 'VERIFIED').length;
		this.status = 'success';
		this.#seededFor = seededFor ?? null;
	}

	async load(query: string, filter: BrowseFilter): Promise<void> {
		if (this.#seededFor && this.#seededFor.query === query && this.#seededFor.filter === filter) {
			// The current render already has this exact server-loaded list.
			this.#seededFor = null;
			return;
		}

		const generation = ++this.#generation;
		this.status = 'loading';
		try {
			const list = await searchListings(query, filter === 'HAND_CUT', filter === 'DELIVERY');
			if (generation !== this.#generation) return;
			this.restaurants = list;
			this.verifiedCount = list.filter((x) => verificationStatus(x) === 'VERIFIED').length;
			this.status = 'success';
		} catch {
			if (generation !== this.#generation) return;
			this.status = 'error';
		}
	}

	retry(query: string, filter: BrowseFilter): Promise<void> {
		return this.load(query, filter);
	}
}
