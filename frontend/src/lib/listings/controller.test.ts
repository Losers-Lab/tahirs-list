import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Restaurant } from './restaurants';

vi.mock('./data', async (importOriginal) => {
	const original = await importOriginal<typeof import('./data')>();
	return { ...original, searchListings: vi.fn() };
});
import { searchListings } from './data';
import { ListingsController } from './controller.svelte';

const searchMock = vi.mocked(searchListings);

function spot(id: string): Restaurant {
	return {
		id,
		name: `Spot ${id}`,
		address: '1 Main St',
		lat: 1,
		lng: 2,
		cuisine: 'Halal',
		isHandCut: true,
		isDelivery: false
	};
}

beforeEach(() => {
	searchMock.mockReset();
});

describe('ListingsController — SSR seed adoption', () => {
	it('skips the first client fetch when the server list matches the request', async () => {
		const controller = new ListingsController([spot('a')], { query: '', filter: 'ALL' });

		await controller.load('', 'ALL');

		expect(searchMock).not.toHaveBeenCalled();
		expect(controller.status).toBe('success');
		expect(controller.restaurants.map((r) => r.id)).toEqual(['a']);
	});

	it('a mismatched request invalidates the seed so a later matching call still fetches', async () => {
		const controller = new ListingsController([spot('a')], { query: '', filter: 'ALL' });
		searchMock.mockResolvedValue([spot('b')]);

		// Chip change: must fetch, and must clear the pending seed.
		await controller.load('', 'HAND_CUT');
		expect(searchMock).toHaveBeenCalledTimes(1);

		// Back to All: no stale seed may skip this fetch.
		await controller.load('', 'ALL');
		expect(searchMock).toHaveBeenCalledTimes(2);
	});

	it('adopt() replaces the list and re-arms the seed for the new query', async () => {
		const controller = new ListingsController([spot('a')], { query: '', filter: 'ALL' });
		controller.adopt([spot('b')], { query: 'b', filter: 'ALL' });

		await controller.load('b', 'ALL');
		expect(searchMock).not.toHaveBeenCalled();
		expect(controller.restaurants.map((r) => r.id)).toEqual(['b']);
		expect(controller.verifiedCount).toBe(0);
	});

	it('discards a stale response when the request changes mid-flight', async () => {
		const controller = new ListingsController();
		let resolveFirst: (value: Restaurant[]) => void = () => {};
		searchMock.mockImplementationOnce(
			() => new Promise<Restaurant[]>((resolve) => (resolveFirst = resolve))
		);
		searchMock.mockResolvedValueOnce([spot('new')]);

		const first = controller.load('one', 'ALL');
		const second = controller.load('two', 'ALL');
		resolveFirst([spot('stale')]);
		await Promise.all([first, second]);

		expect(controller.restaurants.map((r) => r.id)).toEqual(['new']);
	});
});
