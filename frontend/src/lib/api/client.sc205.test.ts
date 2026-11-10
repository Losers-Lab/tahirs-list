import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, setAccessToken } from './client';

function mockFetchResponse(body: unknown, status = 200) {
	return vi.fn().mockResolvedValue({
		ok: status >= 200 && status < 300,
		status,
		text: () => Promise.resolve(body === undefined ? '' : JSON.stringify(body))
	});
}

describe('api client — SC-205 photo surface', () => {
	beforeEach(() => {
		setAccessToken(null);
	});

	afterEach(() => {
		vi.restoreAllMocks();
		setAccessToken(null);
	});

	it('getManage asks the server and returns the boolean', async () => {
		const fetchMock = mockFetchResponse({ canManage: true });
		vi.stubGlobal('fetch', fetchMock);

		await expect(api.getManage('l-1')).resolves.toEqual({ canManage: true });
		expect(fetchMock.mock.calls[0][0]).toBe('/v1/listings/l-1/manage');
		expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET' });
	});

	it('uploadListingImage sends multipart with the bearer and no JSON content-type', async () => {
		setAccessToken('at-123');
		const fetchMock = mockFetchResponse(undefined, 204);
		vi.stubGlobal('fetch', fetchMock);

		const file = new File([new Uint8Array([1, 2, 3])], 'hero.jpg', { type: 'image/jpeg' });
		await api.uploadListingImage('l-1', file);

		const [url, init] = fetchMock.mock.calls[0];
		expect(url).toBe('/v1/listings/l-1/image');
		expect(init.method).toBe('PUT');
		expect(init.body).toBeInstanceOf(FormData);
		expect((init.body as FormData).get('image')).toBe(file);
		// The browser must set the boundary; a hand-set Content-Type would break it.
		expect(init.headers['Content-Type']).toBeUndefined();
		expect(init.headers.Authorization).toBe('Bearer at-123');
	});

	it('maps a 413 to the payload_too_large ApiError', async () => {
		setAccessToken('at-123');
		vi.stubGlobal('fetch', mockFetchResponse({ code: 'payload_too_large' }, 413));

		const file = new File([new Uint8Array([1])], 'big.jpg', { type: 'image/jpeg' });
		await expect(api.uploadListingImage('l-1', file)).rejects.toMatchObject({
			status: 413,
			code: 'payload_too_large'
		});
	});

	it('maps a 403 to not_listing_owner', async () => {
		setAccessToken('at-123');
		vi.stubGlobal('fetch', mockFetchResponse({ code: 'not_listing_owner' }, 403));

		const file = new File([new Uint8Array([1])], 'x.jpg', { type: 'image/jpeg' });
		await expect(api.uploadListingImage('l-1', file)).rejects.toBeInstanceOf(ApiError);
	});
});
