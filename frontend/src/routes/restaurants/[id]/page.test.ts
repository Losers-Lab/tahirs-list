import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Restaurant } from '#lib/listings/restaurants';
import { sessionState } from '#lib/auth/session.svelte';
import Page from './+page.svelte';

const now = Date.now();
const day = 86_400_000;

vi.mock('$app/state', () => ({
	page: {
		url: new URL('http://localhost/restaurants/l-1'),
		params: { id: 'l-1' }
	}
}));

vi.mock('$app/navigation', () => ({
	goto: vi.fn(),
	afterNavigate: vi.fn()
}));

vi.mock('#lib/listings/data', () => ({
	getRestaurant: vi.fn()
}));

import { getRestaurant } from '#lib/listings/data';

const getRestaurantMock = vi.mocked(getRestaurant);

function restaurant(over: Partial<Restaurant>): Restaurant {
	return {
		id: 'l-1',
		name: 'Al-Amir Grill',
		address: '112 Atlantic Ave, Brooklyn, NY',
		lat: 40.6916,
		lng: -73.9788,
		cuisine: 'Middle Eastern',
		isHandCut: true,
		isDelivery: false,
		rating: 4.6,
		reviewCount: 89,
		distanceMi: 1.2,
		...over
	};
}

describe('RestaurantDetailPage — certificate trust panel (detail-page.md)', () => {
	beforeEach(() => {
		getRestaurantMock.mockReset();
		sessionState.current = null;
		sessionState.restoring = false;
	});

	it('renders the verified certificate panel with certifier, review, expiry + view link', async () => {
		getRestaurantMock.mockResolvedValueOnce(
			restaurant({
				certificate: {
					certifier: 'HFSAA',
					reviewedOn: new Date(now - 200 * day).toISOString(),
					expiresOn: new Date(now + 300 * day).toISOString(),
					certificateUrl: 'https://example.com/cert.pdf'
				}
			})
		);
		render(Page);

		expect(await screen.findByText('Halal verification')).toBeInTheDocument();
		// <dl> field values
		expect(screen.getByText('HFSAA')).toBeInTheDocument();
		const link = screen.getByRole('link', {
			name: 'View halal certificate for Al-Amir Grill'
		});
		expect(link).toHaveAttribute('href', 'https://example.com/cert.pdf');
		expect(screen.getByText(/Reviewed by our verification committee/)).toBeInTheDocument();
	});

	it('shows an expiring-soon warning (icon+text) when ≤ 60 days to expiry', async () => {
		getRestaurantMock.mockResolvedValueOnce(
			restaurant({
				certificate: {
					certifier: 'HFSAA',
					reviewedOn: new Date(now - 100 * day).toISOString(),
					expiresOn: new Date(now + 20 * day).toISOString()
				}
			})
		);
		render(Page);

		const notice = await screen.findByText(/review in progress/);
		expect(notice).toBeInTheDocument();
		// Never danger-red for an expiring/expired state (binding rule).
	});

	it('renders the quiet unverified panel (never red) for a listing with no certificate', async () => {
		getRestaurantMock.mockResolvedValueOnce(restaurant({}));
		render(Page);

		expect(await screen.findByText(/hasn't been verified yet/i)).toBeInTheDocument();
		expect(screen.queryByText('Halal verification')).not.toBeInTheDocument();
		expect(screen.queryByText('Verified')).not.toBeInTheDocument();
		// Exactly ONE "Unverified" badge — the title-row one. The redundant badge
		// inside the status panel was removed (sc-185 Item 3).
		expect(screen.getAllByText('Unverified')).toHaveLength(1);
	});

	it('renders the not-found panel when the id is unknown (header/footer intact)', async () => {
		getRestaurantMock.mockResolvedValueOnce(undefined);
		render(Page);

		expect(await screen.findByText("We couldn't find that restaurant.")).toBeInTheDocument();
		expect(screen.getByRole('link', { name: 'Browse all spots' })).toHaveAttribute(
			'href',
			'/search'
		);
		expect(screen.getByRole('banner')).toBeInTheDocument();
		expect(screen.getByRole('contentinfo')).toBeInTheDocument();
	});

	it('renders the error panel and retries on fetch failure', async () => {
		getRestaurantMock.mockRejectedValueOnce(new Error('boom'));
		getRestaurantMock.mockResolvedValueOnce(
			restaurant({
				certificate: {
					certifier: 'HFSAA',
					reviewedOn: new Date(now - 200 * day).toISOString(),
					expiresOn: new Date(now + 300 * day).toISOString()
				}
			})
		);
		const user = userEvent.setup();
		render(Page);

		expect(await screen.findByText("We couldn't load this restaurant.")).toBeInTheDocument();

		const retry = screen.getByRole('button', { name: 'Retry' });
		await user.click(retry);
		await waitFor(() => expect(screen.getByText('Halal verification')).toBeInTheDocument());
	});

	it('detail hero requests the FULL-res variant (imageUrl), eager (sc-157)', async () => {
		getRestaurantMock.mockResolvedValueOnce(
			restaurant({
				id: 'l-1',
				name: 'Al-Amir Grill',
				imageThumbnailUrl: '/v1/listings/l-1/image?variant=thumbnail',
				imageUrl: '/v1/listings/l-1/image?variant=full'
			})
		);
		render(Page);

		const img = await screen.findByRole('img', { name: 'Al-Amir Grill' });
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=full');
		// Full-res hero is the LCP element — load it immediately, not lazy.
		expect(img).toHaveAttribute('loading', 'eager');
	});

	it('renders the quiet image placeholder (no <img>) when a listing has no photo (sc-157)', async () => {
		getRestaurantMock.mockResolvedValueOnce(restaurant({ id: 'l-9', name: 'New Spot' }));
		render(Page);

		expect(await screen.findByText('New Spot')).toBeInTheDocument();
		expect(screen.queryByRole('img', { name: 'New Spot' })).not.toBeInTheDocument();
	});

	it('renders the full comma-form address and an embedded map preview with a pin (sc-187)', async () => {
		getRestaurantMock.mockResolvedValueOnce(
			restaurant({
				address: '112 Atlantic Ave',
				city: 'Brooklyn',
				province: 'NY',
				postal: '11201'
			})
		);
		render(Page);

		// Full structured address (street, city, "province postal") from the read surface.
		expect(await screen.findByText('112 Atlantic Ave, Brooklyn, NY 11201')).toBeInTheDocument();

		// "Get directions" deep link targets the same full address string.
		const directions = screen.getByRole('link', { name: 'Get directions' });
		expect(directions).toHaveAttribute(
			'href',
			`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent('112 Atlantic Ave, Brooklyn, NY 11201')}`
		);

		// Embedded map preview pinned at the listing's lat/lng — Google Maps oEmbed.
		const frame = screen.getByTitle('Map showing the location of Al-Amir Grill');
		expect(frame).toHaveAttribute(
			'src',
			'https://www.google.com/maps?q=40.6916,-73.9788&z=16&output=embed'
		);
	});

	it('renders the flat address line when structured fields are absent (pre-ingest/legacy fallback)', async () => {
		getRestaurantMock.mockResolvedValueOnce(
			restaurant({ address: '112 Atlantic Ave, Brooklyn, NY' })
		);
		render(Page);

		expect(await screen.findByText('112 Atlantic Ave, Brooklyn, NY')).toBeInTheDocument();
		const directions = screen.getByRole('link', { name: 'Get directions' });
		expect(directions).toHaveAttribute(
			'href',
			`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent('112 Atlantic Ave, Brooklyn, NY')}`
		);
	});

	it('renders the Delivery service-mode chip when the listing offers delivery (sc-184)', async () => {
		getRestaurantMock.mockResolvedValueOnce(restaurant({ isDelivery: true }));
		render(Page);

		const delivery = await screen.findByText('Delivery');
		expect(delivery.closest('span')).toHaveAttribute('title', 'This spot offers delivery');
	});

	it('does not show a Delivery chip for a pickup-only/unknown listing (sc-184)', async () => {
		getRestaurantMock.mockResolvedValueOnce(restaurant({ isDelivery: false }));
		render(Page);

		expect(await screen.findByText('Al-Amir Grill')).toBeInTheDocument();
		expect(screen.queryByText('Delivery')).not.toBeInTheDocument();
	});
});

describe('RestaurantDetailPage — SSR data adoption', () => {
	beforeEach(() => {
		getRestaurantMock.mockReset();
		sessionState.current = null;
		sessionState.restoring = false;
	});

	it('renders server-loaded detail without a client fetch (crawlable HTML)', () => {
		render(Page, { data: { restaurant: restaurant({ name: 'Seed Diner' }) } });

		expect(screen.getByRole('heading', { name: 'Seed Diner' })).toBeInTheDocument();
		expect(getRestaurantMock).not.toHaveBeenCalled();
	});

	it('falls back to the client fetch when the SSR load failed', async () => {
		getRestaurantMock.mockResolvedValueOnce(restaurant({ name: 'Recovered Spot' }));
		render(Page, { data: { restaurant: null, failed: true } });

		expect(await screen.findByRole('heading', { name: 'Recovered Spot' })).toBeInTheDocument();
		expect(getRestaurantMock).toHaveBeenCalledWith('l-1');
	});
});
