import { fireEvent, render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Restaurant } from '#lib/listings/restaurants';
import Page from './+page.svelte';

vi.mock('$app/state', () => ({
	page: {
		url: new URL('http://localhost/search'),
		params: {}
	}
}));

vi.mock('$app/navigation', () => ({
	goto: vi.fn()
}));

// The page's ListingsController imports `./data`; the alias resolves to the
// same module id, so this mock intercepts both spellings.
vi.mock('#lib/listings/data', () => ({
	searchListings: vi.fn()
}));

import { searchListings } from '#lib/listings/data';

const searchMock = vi.mocked(searchListings);

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

describe('SearchPage — search-first + browse chips (search-browse.md)', () => {
	beforeEach(() => {
		searchMock.mockReset();
	});

	it('renders a result grid from the data layer and links each card to its detail page', async () => {
		searchMock.mockResolvedValue([
			restaurant({ id: 'l-1', name: 'Al-Amir Grill' }),
			restaurant({ id: 'l-2', name: 'Karachi Kitchen' })
		]);
		render(Page);

		expect(await screen.findByRole('link', { name: 'Al-Amir Grill' })).toHaveAttribute(
			'href',
			'/restaurants/l-1'
		);
		expect(screen.getByRole('link', { name: 'Karachi Kitchen' })).toHaveAttribute(
			'href',
			'/restaurants/l-2'
		);
	});

	it('cards source the WIDEST imageSrcset title variant — never the full-res original (sc-183)', async () => {
		searchMock.mockResolvedValue([
			restaurant({
				id: 'l-1',
				name: 'Al-Amir Grill',
				imageThumbnailUrl: '/v1/listings/l-1/image?variant=thumbnail',
				imageSrcset: [
					{ width: 400, url: '/v1/listings/l-1/image?variant=thumbnail' },
					{ width: 768, url: '/v1/listings/l-1/image?variant=thumbnail_768' },
					{ width: 1280, url: '/v1/listings/l-1/image?variant=thumbnail_1280' },
					{ width: 1920, url: '/v1/listings/l-1/image?variant=thumbnail_1920' }
				],
				imageUrl: '/v1/listings/l-1/image?variant=full'
			})
		]);
		render(Page);

		const img = await screen.findByRole('img', { name: 'Al-Amir Grill' });
		// Cards source the widest variant so every responsive width is sharp
		// (mobile 100vw @ DPR3 → desktop).
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=thumbnail_1920');
		// sizes="100vw" = founder's max-monitor-context baseline (sc-183).
		expect(img).toHaveAttribute('sizes', '100vw');
		// All backend variants are offered so the browser can pick per DPR.
		expect(img).toHaveAttribute(
			'srcset',
			'/v1/listings/l-1/image?variant=thumbnail 400w, /v1/listings/l-1/image?variant=thumbnail_768 768w, /v1/listings/l-1/image?variant=thumbnail_1280 1280w, /v1/listings/l-1/image?variant=thumbnail_1920 1920w'
		);
		// The full-res URL exists on the read-model but a card must not request it.
		expect(img).not.toHaveAttribute('src', '/v1/listings/l-1/image?variant=full');
		// Lazy: off-screen cards defer the transfer.
		expect(img).toHaveAttribute('loading', 'lazy');
	});

	it('cards fall back to the small thumbnail src when the backend sends no srcset', async () => {
		searchMock.mockResolvedValue([
			restaurant({
				id: 'l-1',
				name: 'Al-Amir Grill',
				imageThumbnailUrl: '/v1/listings/l-1/image?variant=thumbnail'
			})
		]);
		render(Page);

		const img = await screen.findByRole('img', { name: 'Al-Amir Grill' });
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=thumbnail');
	});

	it('renders server-loaded cards without a client fetch (SSR/SEO)', () => {
		const seeded = [restaurant({ id: 'l-9', name: 'Seed Spot' })];
		render(Page, { data: { listings: seeded } });

		expect(screen.getByRole('link', { name: 'Seed Spot' })).toBeInTheDocument();
		expect(searchMock).not.toHaveBeenCalled();
	});

	it('shows a verified badge on verified cards and an unverified tag otherwise', async () => {
		const future = new Date(Date.now() + 300 * 86_400_000).toISOString();
		searchMock.mockResolvedValue([
			restaurant({
				id: 'l-1',
				name: 'Verified Spot',
				certificate: {
					certifier: 'HFSAA',
					reviewedOn: new Date().toISOString(),
					expiresOn: future
				}
			}),
			restaurant({ id: 'l-2', name: 'Plain Spot' })
		]);
		render(Page);

		const verified = await screen.findAllByText('Verified');
		expect(verified.length).toBe(2); // on-photo + card body badge
		expect(screen.getByText('Unverified')).toBeInTheDocument();
	});

	it('empty results render the quiet empty panel, not an error', async () => {
		searchMock.mockResolvedValue([]);
		render(Page);

		expect(await screen.findByText('Nothing matches yet')).toBeInTheDocument();
		// Never an error surface for zero results.
		expect(screen.queryByText(/couldn't load/i)).not.toBeInTheDocument();
	});

	it('browse chip filters through the data layer with aria-pressed', async () => {
		searchMock.mockResolvedValue([]);
		const user = userEvent.setup();
		render(Page);

		const handCut = await screen.findByRole('button', { name: 'Hand-cut' });
		expect(handCut).toHaveAttribute('aria-pressed', 'false');
		await user.click(handCut);
		await waitFor(() => expect(searchMock).toHaveBeenCalledWith('', true, false));
		await waitFor(() => expect(handCut).toHaveAttribute('aria-pressed', 'true'));
	});

	it('the Delivery chip narrows the backend read to delivery-only (sc-184)', async () => {
		searchMock.mockResolvedValue([]);
		const user = userEvent.setup();
		render(Page);

		const delivery = await screen.findByRole('button', { name: 'Delivery' });
		expect(delivery).toHaveAttribute('aria-pressed', 'false');
		await user.click(delivery);
		// ListingsController maps the DELIVERY chip to the third (deliveryOnly) boolean.
		await waitFor(() => expect(searchMock).toHaveBeenCalledWith('', false, true));
		await waitFor(() => expect(delivery).toHaveAttribute('aria-pressed', 'true'));
	});

	it('renders the Delivery chip on cards that offer delivery, not on pickup-only (sc-184)', async () => {
		searchMock.mockResolvedValue([
			restaurant({ id: 'd1', name: 'Deliveroo', isDelivery: true }),
			restaurant({ id: 'p1', name: 'Pickup Only', isDelivery: false })
		]);
		render(Page);

		expect(await screen.findByText('Deliveroo')).toBeInTheDocument();
		// Exactly ONE card carries the Delivery service-mode indicator (the chip's
		// `title` is unique to the card indicator — the filter chip has no title).
		// The pickup-only card carries none.
		expect(screen.getAllByTitle('This spot offers delivery')).toHaveLength(1);
	});

	it('a legacy card still renders its stored thumbnail when the widest srcset variant 404s (sc-183 fallback)', async () => {
		searchMock.mockResolvedValue([
			restaurant({
				id: 'l-1',
				name: 'Al-Amir Grill',
				// Backend advertises the full width set for every listing, but a row
				// ingested before multi-width thumbnails stored only thumbnail(400)+full.
				imageThumbnailUrl: '/v1/listings/l-1/image?variant=thumbnail',
				imageSrcset: [
					{ width: 400, url: '/v1/listings/l-1/image?variant=thumbnail' },
					{ width: 768, url: '/v1/listings/l-1/image?variant=thumbnail_768' },
					{ width: 1280, url: '/v1/listings/l-1/image?variant=thumbnail_1280' },
					{ width: 1920, url: '/v1/listings/l-1/image?variant=thumbnail_1920' }
				]
			})
		]);
		render(Page);

		const img = await screen.findByRole('img', { name: 'Al-Amir Grill' });
		// Card sources the widest variant first (sharp srcset for fully-ingested rows).
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=thumbnail_1920');

		// Legacy row: the widest variant was never stored → 404.
		await fireEvent.error(img);

		// The card steps down to the guaranteed thumbnail — a valid photo always
		// renders (sc-157) instead of dropping to the blank placeholder.
		await waitFor(() =>
			expect(screen.getByRole('img', { name: 'Al-Amir Grill' })).toHaveAttribute(
				'src',
				'/v1/listings/l-1/image?variant=thumbnail'
			)
		);
	});
});
