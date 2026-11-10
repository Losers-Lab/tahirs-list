import { render, screen, waitFor } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ListingPhotoPanel from './ListingPhotoPanel.svelte';

vi.mock('#lib/api/client', () => ({
	api: { uploadListingImage: vi.fn() },
	ApiError: class ApiError extends Error {
		status: number;
		code: string;
		constructor(status: number, code: string) {
			super(code);
			this.status = status;
			this.code = code;
		}
	}
}));

vi.mock('$app/state', () => ({
	page: { url: new URL('http://localhost/restaurants/l-1') }
}));

import { ApiError, api } from '#lib/api/client';

const uploadMock = vi.mocked(api.uploadListingImage);

function props(over: Partial<Record<string, unknown>> = {}) {
	return {
		listingId: 'l-1',
		name: 'Al-Amir Grill',
		src: 'http://localhost/v1/listings/l-1/image?variant=full&v=7',
		fallbackSrc: 'http://localhost/v1/listings/l-1/image?variant=thumbnail_400&v=7',
		canManage: true,
		onUploaded: vi.fn(),
		...over
	};
}

describe('ListingPhotoPanel (SC-205)', () => {
	afterEach(() => {
		uploadMock.mockReset();
		vi.restoreAllMocks();
	});

	it('shows the empty state and an Add control when a manager has no photo yet', () => {
		render(ListingPhotoPanel, props({ src: undefined, fallbackSrc: undefined }));

		expect(screen.getByText('No photo yet')).toBeInTheDocument();
		expect(screen.getByRole('button', { name: /Add a photo/ })).toBeInTheDocument();
	});

	it('labels the control Replace when a photo already exists', () => {
		render(ListingPhotoPanel, props());

		expect(screen.getByRole('button', { name: /Replace photo/ })).toBeInTheDocument();
	});

	it('never offers the control to a viewer the server says may not manage', () => {
		render(ListingPhotoPanel, props({ canManage: false }));

		expect(
			screen.queryByRole('button', { name: /Add a photo|Replace photo/ })
		).not.toBeInTheDocument();
	});

	it('uploads the chosen file and tells the page to re-read on success', async () => {
		uploadMock.mockResolvedValue(undefined);
		const onUploaded = vi.fn();
		const { container } = render(ListingPhotoPanel, props({ onUploaded }));

		await userEvent.click(screen.getByRole('button', { name: /Replace photo/ }));
		const input = container.querySelectorAll('input[type="file"]')[0];
		const file = new File([new Uint8Array([1, 2, 3])], 'hero.jpg', { type: 'image/jpeg' });
		await userEvent.upload(input as HTMLInputElement, file);

		await waitFor(() => expect(uploadMock).toHaveBeenCalledWith('l-1', file));
		await waitFor(() => expect(onUploaded).toHaveBeenCalledTimes(1));
	});

	it('keeps the dialog open and explains a refusal without calling onUploaded', async () => {
		const error = new ApiError(403, 'not_listing_owner');
		uploadMock.mockRejectedValue(error);
		const onUploaded = vi.fn();
		const { container } = render(ListingPhotoPanel, props({ onUploaded }));

		await userEvent.click(screen.getByRole('button', { name: /Replace photo/ }));
		const input = container.querySelectorAll('input[type="file"]')[0];
		await userEvent.upload(
			input as HTMLInputElement,
			new File([new Uint8Array([1])], 'x.jpg', { type: 'image/jpeg' })
		);

		expect(await screen.findByRole('alert')).toHaveTextContent(/owner or an administrator/i);
		expect(onUploaded).not.toHaveBeenCalled();
	});
});
