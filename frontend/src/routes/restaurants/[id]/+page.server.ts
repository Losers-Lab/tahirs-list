import type { PageServerLoad } from './$types';
import { apiBase } from '#lib/server/api';
import { toRestaurant } from '#lib/listings/data';
import type { ListingDetail } from '#lib/api/client';

/**
 * Restaurant detail SSR: the trust panel, address, hours and hero are in the
 * initial HTML for crawlers. 404 stays a page-level "not found" panel (chrome
 * intact); other failures degrade to the client fetch/error state.
 */
export const load: PageServerLoad = async ({ fetch, params }) => {
	try {
		const response = await fetch(`${apiBase()}/v1/listings/${encodeURIComponent(params.id)}`, {
			signal: AbortSignal.timeout(2_000)
		});
		if (response.status === 404) return { restaurant: null, notFound: true };
		if (!response.ok) return { restaurant: null, failed: true };
		const detail = (await response.json()) as ListingDetail;
		return { restaurant: toRestaurant(detail) };
	} catch {
		return { restaurant: null, failed: true };
	}
};
