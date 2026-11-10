import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import PhotoCredit from './PhotoCredit.svelte';

/**
 * SC-231 — the credit a photo's source requires.
 *
 * The rule is that credit is never invented and never hidden: a photo with an
 * attribution shows it (linked when the source page is known), and a listing with
 * no photo renders nothing at all rather than a generic label.
 */
describe('PhotoCredit', () => {
	it('renders the attribution and licence, linked to the source page', () => {
		render(PhotoCredit, {
			attribution: 'A. Person / Wikimedia Commons',
			licence: 'CC_BY',
			sourceUrl: 'https://commons.wikimedia.org/wiki/File:Example.jpg'
		});

		const link = screen.getByRole('link');
		expect(link).toHaveTextContent('A. Person / Wikimedia Commons · CC_BY');
		expect(link).toHaveAttribute('href', 'https://commons.wikimedia.org/wiki/File:Example.jpg');
		expect(link).toHaveAttribute('rel', 'nofollow noopener noreferrer');
	});

	it('renders a Places credit without a link when the source page is unknown', () => {
		render(PhotoCredit, { attribution: 'Google Maps, Md Abu Rihan' });

		expect(screen.getByText('Google Maps, Md Abu Rihan')).toBeInTheDocument();
		expect(screen.queryByRole('link')).toBeNull();
	});

	it('renders nothing when there is no photo to credit', () => {
		const { container } = render(PhotoCredit, {});

		// Svelte leaves a comment node for the false branch; what matters is no text.
		expect(container.querySelector('p')).toBeNull();
		expect(container.textContent?.trim()).toBe('');
	});
});
