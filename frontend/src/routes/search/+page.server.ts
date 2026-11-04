import type { PageServerLoad } from './$types';
import { apiBase } from '#lib/server/api';
import { filterListings, toRestaurant } from '#lib/listings/data';
import type { BrowseListing } from '#lib/api/client';

/**
 * Search SSR: the server applies the same text filter as the client so the
 * initial HTML contains the matching cards (crawlable), not a skeleton.
 */
export const load: PageServerLoad = async ({ fetch, url }) => {
	const query = url.searchParams.get('q') ?? '';
	try {
		const response = await fetch(`${apiBase()}/v1/listings`);
		if (!response.ok) return { listings: null };
		const cards = (await response.json()) as BrowseListing[];
		return { listings: filterListings(cards.map(toRestaurant), query) };
	} catch {
		return { listings: null };
	}
};
