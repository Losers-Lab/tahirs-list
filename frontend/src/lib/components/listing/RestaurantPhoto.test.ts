import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import RestaurantPhoto from './RestaurantPhoto.svelte';

/**
 * sc-157 — RestaurantPhoto is the variant-selecting image renderer. Cards pass
 * the SMALL thumbnail URL; the detail hero passes the FULL URL eager. The main
 * behaviours to lock in:
 *  - the URL is used verbatim as the `<img>` src (frontend never rewrites it);
 *  - `sizes`/`alt` are forwarded for correct srcset + accessibility;
 *  - cards lazy-load, the detail hero loads eager;
 *  - a listing without an ingested photo renders the quiet placeholder (no img);
 *  - a FAILED load (onerror) renders the SAME placeholder, never a broken-image
 *    icon (sc-171 founder directive / detail-page.md §1.1).
 */
describe('RestaurantPhoto', () => {
	it('renders the provided variant URL as the img src with the restaurant name as alt', () => {
		render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=thumbnail',
			alt: 'Al-Amir Grill',
			sizes: '25vw'
		});

		const img = screen.getByRole('img', { name: 'Al-Amir Grill' });
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=thumbnail');
		// sizes forwarded so the browser can pick a responsive candidate.
		expect(img).toHaveAttribute('sizes', '25vw');
	});

	it('lazy-loads by default (cards) and can be forced eager (detail hero)', () => {
		const first = render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=thumbnail',
			alt: 'a',
			sizes: '100vw'
		});
		expect(screen.getByRole('img', { name: 'a' })).toHaveAttribute('loading', 'lazy');

		first.unmount();

		render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=full',
			alt: 'a',
			sizes: '100vw',
			eager: true
		});
		expect(screen.getByRole('img', { name: 'a' })).toHaveAttribute('loading', 'eager');
	});

	it('renders the quiet placeholder (no image) when no photo is ingested yet', () => {
		const { container } = render(RestaurantPhoto, { alt: 'No photo yet', sizes: '100vw' });

		expect(screen.queryByRole('img')).not.toBeInTheDocument();
		expect(container.querySelector('svg')).toBeInTheDocument();
	});

	it('swaps to the same stamp placeholder when the image fails to load (no broken-image icon)', async () => {
		const { container } = render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=thumbnail',
			alt: 'Al-Amir Grill',
			sizes: '25vw'
		});

		const img = screen.getByRole('img', { name: 'Al-Amir Grill' });
		await fireEvent.error(img);

		// The failed <img> is gone; the reusable kraft-placeholder is shown instead.
		expect(screen.queryByRole('img')).not.toBeInTheDocument();
		expect(container.querySelector('svg')).toBeInTheDocument();
	});

	it('falls back to fallbackSrc when the primary (widest) variant 404s, instead of a blank placeholder (sc-183 legacy listing)', async () => {
		const { container } = render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=thumbnail_1920',
			fallbackSrc: '/v1/listings/l-1/image?variant=thumbnail',
			alt: 'Al-Amir Grill',
			sizes: '100vw'
		});

		// The widest variant renders first...
		let img = screen.getByRole('img', { name: 'Al-Amir Grill' });
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=thumbnail_1920');

		// ...fails (404: variant not stored for a pre-multi-width listing)...
		await fireEvent.error(img);

		// ...and the card steps down to the guaranteed ≤400px thumbnail — no blank.
		img = screen.getByRole('img', { name: 'Al-Amir Grill' });
		expect(img).toHaveAttribute('src', '/v1/listings/l-1/image?variant=thumbnail');
		expect(container.querySelector('svg')).not.toBeInTheDocument();
	});

	it('shows the placeholder when the primary AND the fallback both fail', async () => {
		const { container } = render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=thumbnail_1920',
			fallbackSrc: '/v1/listings/l-1/image?variant=thumbnail',
			alt: 'Al-Amir Grill',
			sizes: '100vw'
		});

		let img = screen.getByRole('img', { name: 'Al-Amir Grill' });
		await fireEvent.error(img); // primary fails -> step to thumbnail
		img = screen.getByRole('img', { name: 'Al-Amir Grill' });
		await fireEvent.error(img); // thumbnail also fails -> placeholder

		expect(screen.queryByRole('img')).not.toBeInTheDocument();
		expect(container.querySelector('svg')).toBeInTheDocument();
	});

	it('does not retry the same URL twice when primary and fallback are identical', async () => {
		const { container } = render(RestaurantPhoto, {
			src: '/v1/listings/l-1/image?variant=thumbnail',
			fallbackSrc: '/v1/listings/l-1/image?variant=thumbnail',
			alt: 'Al-Amir Grill',
			sizes: '100vw'
		});

		await fireEvent.error(screen.getByRole('img', { name: 'Al-Amir Grill' }));

		// Deduped → one failure exhausts the chain → placeholder immediately.
		expect(screen.queryByRole('img')).not.toBeInTheDocument();
		expect(container.querySelector('svg')).toBeInTheDocument();
	});
});
