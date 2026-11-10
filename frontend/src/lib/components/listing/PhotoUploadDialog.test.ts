import { render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import PhotoUploadDialog from './PhotoUploadDialog.svelte';

vi.mock('$app/state', () => ({
	page: { url: new URL('http://localhost/restaurants/l-1') }
}));

function baseProps(over: Partial<Record<string, unknown>> = {}) {
	return {
		open: true,
		phase: 'idle' as const,
		error: null,
		replacing: false,
		onchoose: vi.fn(),
		oncancel: vi.fn(),
		onretry: vi.fn(),
		...over
	};
}

describe('PhotoUploadDialog (SC-205)', () => {
	it('offers both ways to supply a photo: an existing file and the camera', () => {
		const { container } = render(PhotoUploadDialog, baseProps());

		expect(screen.getByRole('button', { name: 'Choose a photo' })).toBeInTheDocument();
		expect(screen.getByRole('button', { name: 'Take a photo' })).toBeInTheDocument();

		const inputs = container.querySelectorAll('input[type="file"]');
		expect(inputs).toHaveLength(2);
		expect(inputs[0]).toHaveAttribute('accept', 'image/*');
		expect(inputs[0].getAttribute('capture')).toBeNull();
		// The camera input asks phones for the rear camera; desktop ignores it.
		expect(inputs[1]).toHaveAttribute('capture', 'environment');
	});

	it('discloses the loss before replacing an existing photo', () => {
		render(PhotoUploadDialog, baseProps({ replacing: true }));

		expect(screen.getByRole('heading', { name: 'Replace the photo' })).toBeInTheDocument();
		expect(screen.getByText(/previous one cannot be restored/i)).toBeInTheDocument();
	});

	it('does not claim a loss when the listing has no photo yet', () => {
		render(PhotoUploadDialog, baseProps({ replacing: false }));

		expect(screen.getByRole('heading', { name: 'Add a photo' })).toBeInTheDocument();
		expect(screen.queryByText(/cannot be restored/i)).not.toBeInTheDocument();
	});

	it('announces an error and offers a retry (except when the session is the problem)', () => {
		render(
			PhotoUploadDialog,
			baseProps({
				phase: 'error',
				error: { kind: 'server', message: "We couldn't save that photo. Try again." }
			})
		);

		expect(screen.getByRole('alert')).toHaveTextContent(/couldn't save that photo/i);
		expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument();
	});

	it('points at sign-in instead of a retry when the session expired', () => {
		render(
			PhotoUploadDialog,
			baseProps({
				phase: 'error',
				error: { kind: 'auth', message: 'Your session expired.' }
			})
		);

		expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
			'href',
			'/login?redirect=%2Frestaurants%2Fl-1'
		);
		expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument();
	});
});
