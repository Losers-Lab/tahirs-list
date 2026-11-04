import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MobileTabBar from './MobileTabBar.svelte';

describe('MobileTabBar (docs/design/mobile-bottom-tab.md)', () => {
	it('is hidden on desktop (lg+) so it never overlays desktop layouts (sc-185 Item 4)', () => {
		render(MobileTabBar, { pathname: '/search' });

		const nav = screen.getByRole('navigation', { name: 'Primary' });
		// lg:hidden hides ≥ lg while leaving it visible on mobile; a bare `hidden`
		// would hide it everywhere and is a regression.
		expect(nav).toHaveClass('lg:hidden');
		expect(nav).not.toHaveClass('hidden');
	});

	it('renders the four tabs in fixed order', () => {
		render(MobileTabBar, { pathname: '/search' });

		const links = screen.getAllByRole('link');
		expect(links).toHaveLength(4);
		expect(links.map((link) => link.getAttribute('href'))).toEqual([
			'/search',
			'/add-listing',
			'/saved',
			'/account'
		]);
		expect(screen.getByRole('link', { name: 'Search' })).toHaveAttribute('href', '/search');
		expect(screen.getByRole('link', { name: 'Add' })).toHaveAttribute('href', '/add-listing');
		expect(screen.getByRole('link', { name: 'Saved' })).toHaveAttribute('href', '/saved');
		expect(screen.getByRole('link', { name: 'Account' })).toHaveAttribute('href', '/account');
	});

	it('marks the current route with aria-current and treats / as Search', () => {
		render(MobileTabBar, { pathname: '/' });

		expect(screen.getByRole('link', { name: 'Search' })).toHaveAttribute('aria-current', 'page');
		expect(screen.getByRole('link', { name: 'Saved' })).not.toHaveAttribute('aria-current');
	});

	it('marks only the matching tab on a nested route', () => {
		render(MobileTabBar, { pathname: '/saved' });

		expect(screen.getByRole('link', { name: 'Saved' })).toHaveAttribute('aria-current', 'page');
		expect(screen.getByRole('link', { name: 'Search' })).not.toHaveAttribute('aria-current');
	});
});
