import { waitFor } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '#lib/api/client';
import type { Restaurant } from '#lib/listings/restaurants';
import { favorites } from './store.svelte';

// The store imports `../listings/data`; Vitest matches mocks by resolved id.
vi.mock('#lib/listings/data', () => ({ getFavorites: vi.fn() }));

import { getFavorites } from '#lib/listings/data';

const getFavoritesMock = vi.mocked(getFavorites);

function restaurant(id = 'l-1', name = 'Al-Amir Grill'): Restaurant {
	return {
		id,
		name,
		address: '112 Atlantic Ave, Brooklyn, NY',
		lat: 40.6916,
		lng: -73.9788,
		cuisine: 'Middle Eastern',
		isHandCut: true,
		isDelivery: false
	};
}

describe('favorites store (sc-50/51/52)', () => {
	beforeEach(() => {
		vi.restoreAllMocks();
		getFavoritesMock.mockReset();
		getFavoritesMock.mockResolvedValue([]);

		// Module-level runes singleton: drop the previous account's state (and
		// invalidate any in-flight fetch) before each test.
		favorites.syncAccount(null);
		favorites.list = [];
		favorites.status = 'idle';
		favorites.error = null;
		favorites.pendingIds = new Set();
	});

	it('fetches the favorites list once a session is present', async () => {
		getFavoritesMock.mockResolvedValue([restaurant()]);

		favorites.syncAccount('acc-1');
		expect(favorites.status).toBe('loading');

		await waitFor(() => expect(favorites.status).toBe('ready'));
		expect(getFavoritesMock).toHaveBeenCalledTimes(1);
		expect(favorites.list).toHaveLength(1);
		expect(favorites.isFavorited('l-1')).toBe(true);
	});

	it('holds nothing and never fetches for an anonymous visitor', () => {
		favorites.syncAccount(null);

		expect(getFavoritesMock).not.toHaveBeenCalled();
		expect(favorites.status).toBe('idle');
		expect(favorites.list).toHaveLength(0);
		expect(favorites.isFavorited('l-1')).toBe(false);
	});

	it('favourites optimistically: flips the heart and grows the /saved list, then POSTs (sc-50)', async () => {
		const favoriteListing = vi.spyOn(api, 'favoriteListing').mockResolvedValue(undefined);

		favorites.syncAccount('acc-1');
		await waitFor(() => expect(favorites.status).toBe('ready'));

		const toggled = favorites.toggle('l-1', restaurant());

		// Optimistic — the heart flips and the list grows before the POST resolves.
		expect(favorites.isFavorited('l-1')).toBe(true);
		expect(favorites.list).toHaveLength(1);

		await waitFor(() => expect(favoriteListing).toHaveBeenCalledWith('l-1'));
		await toggled;
	});

	it('unfavourites optimistically: removes from the /saved list, then DELETEs (sc-51)', async () => {
		getFavoritesMock.mockResolvedValue([restaurant()]);
		const unfavoriteListing = vi.spyOn(api, 'unfavoriteListing').mockResolvedValue(undefined);

		favorites.syncAccount('acc-1');
		await waitFor(() => expect(favorites.status).toBe('ready'));

		const toggled = favorites.toggle('l-1', restaurant());

		expect(favorites.isFavorited('l-1')).toBe(false);
		expect(favorites.list).toHaveLength(0);

		await waitFor(() => expect(unfavoriteListing).toHaveBeenCalledWith('l-1'));
		await toggled;
	});

	it('reverts the optimistic flip on failure and surfaces a message (never lies)', async () => {
		let fail: (e: Error) => void = () => undefined;
		vi.spyOn(api, 'favoriteListing').mockImplementation(
			() =>
				new Promise<undefined>((_resolve, reject) => {
					fail = reject;
				})
		);

		favorites.syncAccount('acc-1');
		await waitFor(() => expect(favorites.status).toBe('ready'));

		const toggled = favorites.toggle('l-1', restaurant());

		// Flips optimistically before the POST settles…
		expect(favorites.isFavorited('l-1')).toBe(true);
		expect(favorites.list).toHaveLength(1);

		// …then reverts once the POST fails, and a message surfaces.
		fail(new Error('boom'));
		await waitFor(() => expect(favorites.isFavorited('l-1')).toBe(false));
		expect(favorites.error).toMatch(/couldn't save/i);
		await waitFor(() => expect(favorites.list).toHaveLength(0));
		await toggled;
	});

	it('surfaces a load error and recovers on retry, with clearError resetting it', async () => {
		getFavoritesMock.mockRejectedValueOnce(new Error('boom'));

		favorites.syncAccount('acc-1');
		await waitFor(() => expect(favorites.status).toBe('error'));
		expect(favorites.error).toMatch(/couldn't load/i);

		favorites.clearError();
		expect(favorites.error).toBeNull();

		getFavoritesMock.mockResolvedValue([restaurant()]);
		favorites.retry();
		expect(favorites.status).toBe('loading');

		await waitFor(() => expect(favorites.status).toBe('ready'));
		expect(getFavoritesMock).toHaveBeenCalledTimes(2);
		expect(favorites.list).toHaveLength(1);
	});
});
