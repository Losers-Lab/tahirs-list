import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from '../api/client';
import {
	MAX_UPLOAD_BYTES,
	PhotoUploadController,
	mapUploadError,
	validatePhotoFile
} from './photo-upload.svelte';

vi.mock('../api/client', () => ({
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

const uploadMock = vi.mocked(api.uploadListingImage);

function file(size = 1024, type = 'image/jpeg'): File {
	return new File([new Uint8Array(size)], 'photo.jpg', { type });
}

describe('validatePhotoFile (SC-205 pre-flight)', () => {
	it('accepts an image under the cap', () => {
		expect(validatePhotoFile(file(1024, 'image/png'))).toBeNull();
	});

	it('rejects anything over 8 MiB without naming the server', () => {
		expect(validatePhotoFile(file(MAX_UPLOAD_BYTES + 1))).toMatchObject({ kind: 'tooLarge' });
	});

	it('rejects a non-image type', () => {
		expect(validatePhotoFile(file(1024, 'application/pdf'))).toMatchObject({ kind: 'notImage' });
	});
});

describe('mapUploadError', () => {
	it('maps each server status onto the copy the dialog shows', () => {
		expect(mapUploadError(new ApiError(401, 'unauthorized')).kind).toBe('auth');
		expect(mapUploadError(new ApiError(403, 'not_listing_owner')).kind).toBe('forbidden');
		expect(mapUploadError(new ApiError(404, 'listing_not_found')).kind).toBe('notFound');
		expect(mapUploadError(new ApiError(413, 'payload_too_large')).kind).toBe('tooLarge');
		expect(mapUploadError(new ApiError(400, 'invalid_input')).kind).toBe('notImage');
		expect(mapUploadError(new ApiError(500, 'internal_error')).kind).toBe('server');
		expect(mapUploadError(new Error('network_error')).kind).toBe('network');
	});
});

describe('PhotoUploadController (SC-205)', () => {
	beforeEach(() => {
		uploadMock.mockReset();
	});

	afterEach(() => {
		vi.restoreAllMocks();
	});

	it('idle → uploading → idle on success, and reports true', async () => {
		uploadMock.mockResolvedValue(undefined);
		const controller = new PhotoUploadController('l-1');

		const pending = controller.upload(file());
		expect(controller.state).toBe('uploading');
		await expect(pending).resolves.toBe(true);
		expect(controller.state).toBe('idle');
		expect(controller.error).toBeNull();
		expect(uploadMock).toHaveBeenCalledWith('l-1', expect.any(File));
	});

	it('an oversized file never reaches the network', async () => {
		const controller = new PhotoUploadController('l-1');

		await expect(controller.upload(file(MAX_UPLOAD_BYTES + 1))).resolves.toBe(false);
		expect(uploadMock).not.toHaveBeenCalled();
		expect(controller.state).toBe('error');
		expect(controller.error?.kind).toBe('tooLarge');
	});

	it('a 403 leaves an explicit owner-or-admin message and allows a retry', async () => {
		uploadMock.mockRejectedValue(new ApiError(403, 'not_listing_owner'));
		const controller = new PhotoUploadController('l-1');

		await expect(controller.upload(file())).resolves.toBe(false);
		expect(controller.state).toBe('error');
		expect(controller.error?.message).toMatch(/owner or an administrator/i);

		controller.reset();
		expect(controller.state).toBe('idle');
		expect(controller.error).toBeNull();
	});
});
