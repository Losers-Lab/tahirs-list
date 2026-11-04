import type { Restaurant } from './restaurants';
import { verificationStatus } from './restaurants';
import { searchListings, type BrowseFilter } from './data';

export type ListingsStatus = 'loading' | 'success' | 'error';

/**
 * Client-side listing read controller for the browse/search screens.
 *
 * Wraps the async read seam (`data.searchListings`, backed by the live
 * GET /v1/listings) so the UI exercises loading + error states while the search
 * query and chip filters drive refetches. `verifiedCount` is derived during the
 * async read (not in render) so components stay pure.
 *
 * A generation counter discards stale responses when the query/filter changes
 * faster than the network resolves.
 */
export class ListingsController {
	restaurants = $state<Restaurant[]>([]);
	verifiedCount = $state(0);
	status = $state<ListingsStatus>('loading');

	#generation = 0;

	async load(query: string, filter: BrowseFilter): Promise<void> {
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
