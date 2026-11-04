import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { sessionState } from '#lib/auth/session.svelte';
import { favorites } from '#lib/favorites/store.svelte';
import type { Restaurant } from '#lib/listings/restaurants';
import FavoriteButton from './FavoriteButton.svelte';

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
}

describe('FavoriteButton (sc-50/51)', () => {
	beforeEach(() => {
		vi.restoreAllMocks();

		// Reset the module-level runes singletons the component reads.
		sessionState.current = null;
		sessionState.restoring = false;
		favorites.list = [];
		favorites.status = 'ready';
		favorites.error = null;
		favorites.pendingIds = new Set();
	});

	it('renders a login link (not a 401) for an anonymous visitor', () => {
		render(FavoriteButton, { listingId: 'l-1', variant: 'detail' });

		const link = screen.getByRole('link', { name: 'Save to favorites — log in' });
		expect(link).toHaveAttribute('href', '/login');
		expect(link).toHaveTextContent('Save');
		expect(screen.queryByRole('button')).not.toBeInTheDocument();
	});

	it('toggles a listing on for a signed-in user (heart flips + store called)', async () => {
		signedIn();
		const toggle = vi.spyOn(favorites, 'toggle').mockResolvedValue(undefined);
		const clearError = vi.spyOn(favorites, 'clearError');
		const user = userEvent.setup();

		render(FavoriteButton, {
			listingId: 'l-1',
			restaurant: restaurant(),
			variant: 'detail'
		});

		const btn = screen.getByRole('button', { name: 'Save to favorites' });
		expect(btn).toHaveAttribute('aria-pressed', 'false');
		await user.click(btn);

		expect(toggle).toHaveBeenCalledWith('l-1', restaurant());
		expect(clearError).toHaveBeenCalled();
	});

	it('labels a favourited listing as Saved and un-favourites on click', async () => {
		signedIn();
		favorites.list = [restaurant()];
		const toggle = vi.spyOn(favorites, 'toggle').mockResolvedValue(undefined);
		const user = userEvent.setup();

		render(FavoriteButton, { listingId: 'l-1', variant: 'detail' });

		const btn = screen.getByRole('button', { name: 'Remove from favorites' });
		expect(btn).toHaveAttribute('aria-pressed', 'true');
		expect(screen.getByText('Saved')).toBeInTheDocument();

		await user.click(btn);
		expect(toggle).toHaveBeenCalledWith('l-1', undefined);
	});

	it('disables the toggle while the favorites list is still loading', () => {
		signedIn();
		favorites.status = 'loading';

		render(FavoriteButton, { listingId: 'l-1', variant: 'detail' });

		expect(screen.getByRole('button', { name: 'Save to favorites' })).toBeDisabled();
	});

	it('disables the toggle while the session is still restoring', () => {
		signedIn();
		sessionState.restoring = true;

		render(FavoriteButton, { listingId: 'l-1', variant: 'detail' });

		expect(screen.getByRole('button', { name: 'Save to favorites' })).toBeDisabled();
	});

	it('disables the toggle while its own request is pending', () => {
		signedIn();
		favorites.pendingIds = new Set(['l-1']);

		render(FavoriteButton, { listingId: 'l-1', variant: 'detail' });

		expect(screen.getByRole('button', { name: 'Save to favorites' })).toBeDisabled();
	});

	it('shows a toggle failure message on the detail variant (never a silent lie)', () => {
		signedIn();
		favorites.error = "We couldn't save that spot.";

		render(FavoriteButton, { listingId: 'l-1', variant: 'detail' });

		expect(screen.getByRole('alert')).toHaveTextContent("We couldn't save that spot.");
	});

	it('keeps the card variant quiet (no row for the error)', () => {
		signedIn();
		favorites.error = "We couldn't save that spot.";

		render(FavoriteButton, { listingId: 'l-1', variant: 'card' });

		expect(screen.queryByRole('alert')).not.toBeInTheDocument();
	});
});
