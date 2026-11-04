import { render, screen, within } from '@testing-library/svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Restaurant } from '#lib/listings/restaurants';
import { sessionState } from '#lib/auth/session.svelte';
import { favorites } from '#lib/favorites/store.svelte';
import Page from './+page.svelte';

vi.mock('$app/state', () => ({
	page: {
		url: new URL('http://localhost/saved'),
		params: {}
	}
}));

function restaurant(over: Partial<Restaurant> = {}): Restaurant {
	return {
		id: 'l-1',
		name: 'Al-Amir Grill',
		address: '112 Atlantic Ave, Brooklyn, NY',
		lat: 40.6916,
		lng: -73.9788,
		cuisine: 'Middle Eastern',
		isHandCut: true,
		isDelivery: false,
		...over
	};
}

function signedIn() {
	sessionState.current = {
		accessToken: 'at',
		tokenType: 'Bearer',
		expiresAt: Date.now() + 60_000,
		accountId: 'acc-1',
		role: 'USER',
		email: 'a@b.co'
	};
	sessionState.restoring = false;
}

function anonymous() {
	sessionState.current = null;
	sessionState.restoring = false;
}

function favoritesReady(list: Restaurant[]) {
	favorites.list = list;
	favorites.status = 'ready';
	favorites.error = null;
	favorites.pendingIds = new Set<string>();
}

describe('SavedPage — /saved (sc-52)', () => {
	beforeEach(() => {
		anonymous();
		favorites.list = [];
		favorites.status = 'idle';
		favorites.error = null;
		favorites.pendingIds = new Set<string>();
	});

	it('prompts an anonymous visitor to log in (never a 401)', () => {
		favoritesReady([]);
		render(Page);

		const main = screen.getByRole('main');
		expect(
			within(main).getByRole('heading', { name: /log in to see your saved spots/i })
		).toBeInTheDocument();
		expect(within(main).getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
	});

	it('renders the favourited listings as browse cards', () => {
		signedIn();
		favoritesReady([restaurant({ name: 'Al-Amir Grill' })]);
		render(Page);

		expect(screen.getByRole('link', { name: 'Al-Amir Grill' })).toHaveAttribute(
			'href',
			'/restaurants/l-1'
		);
	});

	it('renders a quiet empty state with a browse CTA when nothing is saved', () => {
		signedIn();
		favoritesReady([]);
		render(Page);

		expect(screen.getByRole('heading', { name: 'No saved spots yet' })).toBeInTheDocument();
		const browse = screen.getByRole('link', { name: 'Browse restaurants' });
		expect(browse).toHaveAttribute('href', '/search');
	});
});
