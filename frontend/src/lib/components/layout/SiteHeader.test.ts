import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { tick } from 'svelte';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import SiteHeader from './SiteHeader.svelte';

// The header registers its route-change close through `afterNavigate`; capture
// the callback so a test can fire it the way a real navigation would.
const navigateCallbacks: Array<() => void> = [];

vi.mock('$app/navigation', () => ({
	afterNavigate: (callback: () => void) => {
		navigateCallbacks.push(callback);
	}
}));

vi.mock('#lib/auth/session.svelte', () => ({
	sessionState: { restoring: false, current: null },
	signOut: vi.fn()
}));

const panel = (container: HTMLElement) => container.querySelector('#mobile-nav');

describe('SiteHeader — mobile menu (search-browse.md §Header)', () => {
	beforeEach(() => {
		navigateCallbacks.length = 0;
	});

	it('opens the panel with the secondary links and an auth entry', async () => {
		const user = userEvent.setup();
		const { container } = render(SiteHeader);

		expect(panel(container)).toBeNull();

		await user.click(screen.getByRole('button', { name: 'Open menu' }));

		expect(screen.getByRole('button', { name: 'Close menu' })).toHaveAttribute(
			'aria-expanded',
			'true'
		);
		expect(panel(container)).not.toBeNull();
		expect(panel(container)).toHaveTextContent('Add a restaurant');
		expect(panel(container)?.querySelector('a[href="/login"]')).not.toBeNull();
	});

	it('closes on Escape and returns focus to the trigger', async () => {
		const user = userEvent.setup();
		const { container } = render(SiteHeader);

		await user.click(screen.getByRole('button', { name: 'Open menu' }));
		await user.keyboard('{Escape}');

		expect(panel(container)).toBeNull();
		expect(screen.getByRole('button', { name: 'Open menu' })).toHaveFocus();
	});

	it('closes after a navigation', async () => {
		const user = userEvent.setup();
		const { container } = render(SiteHeader);

		await user.click(screen.getByRole('button', { name: 'Open menu' }));
		expect(panel(container)).not.toBeNull();

		for (const callback of navigateCallbacks) callback();
		await tick();

		expect(panel(container)).toBeNull();
	});
});
