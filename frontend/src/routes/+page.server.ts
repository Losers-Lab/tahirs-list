import type { PageServerLoad } from './$types';
import { apiBase } from '#lib/server/api';
import { filterListings, toRestaurant } from '#lib/listings/data';
import type { BrowseListing } from '#lib/api/client';

/**
 * Home SSR: render the browse grid in the initial HTML (crawlable public
 * listing pages, ARCHITECTURE U-13). Any failure degrades to `null` and the
 * client-side controller fetches after hydration.
 */
export const load: PageServerLoad = async ({ fetch }) => {
	try {
		// Never let a hung backend stall page rendering; degrade to the client fetch.
		const response = await fetch(`${apiBase()}/v1/listings`, {
			signal: AbortSignal.timeout(2_000)
		});
		if (!response.ok) return { listings: null };
		const cards = (await response.json()) as BrowseListing[];
		return { listings: filterListings(cards.map(toRestaurant), '') };
	} catch {
		return { listings: null };
	}
};
