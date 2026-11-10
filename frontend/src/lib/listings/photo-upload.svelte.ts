import { api, ApiError } from '../api/client';

/**
 * SC-205 owner/admin photo upload — the browser side of UC-01.
 *
 * The controller owns the state machine and the *pre-flight* checks; it never
 * decides authorization (the server does, through the same policy that answers
 * `/manage`). Anything it rejects here would also be rejected by the server, so
 * a client that skips this code still cannot bypass the rules.
 */

/** The backend's per-part cap (`MAX_UPLOAD_BYTES` in the web adapter). */
export const MAX_UPLOAD_BYTES = 8 * 1024 * 1024;

export type PhotoUploadState = 'idle' | 'uploading' | 'error';

export type PhotoUploadErrorKind =
	'tooLarge' | 'notImage' | 'auth' | 'forbidden' | 'notFound' | 'server' | 'network';

export type PhotoUploadError = { kind: PhotoUploadErrorKind; message: string };

const MESSAGES: Record<PhotoUploadErrorKind, string> = {
	tooLarge: 'Photos must be 8 MB or smaller.',
	notImage: "That file isn't an image. Choose a JPEG or PNG.",
	auth: 'Your session expired. Sign in again to change this photo.',
	forbidden: "Only this listing's owner or an administrator can change its photo.",
	notFound: 'This listing was removed.',
	server: "We couldn't save that photo. Try again.",
	network: "The upload didn't reach us. Check your connection and try again."
};

/** Pure pre-flight: size and an advisory type check (the server still validates). */
export function validatePhotoFile(file: File): PhotoUploadError | null {
	if (file.size > MAX_UPLOAD_BYTES) {
		return { kind: 'tooLarge', message: MESSAGES.tooLarge };
	}
	if (file.type && !file.type.startsWith('image/')) {
		return { kind: 'notImage', message: MESSAGES.notImage };
	}
	return null;
}

/**
 * Map a failure onto user-facing copy. The backend's 400 carries a generic
 * message by design, so the client owns the "choose a JPEG or PNG" wording.
 */
export function mapUploadError(error: unknown): PhotoUploadError {
	if (error instanceof ApiError) {
		if (error.status === 401) return { kind: 'auth', message: MESSAGES.auth };
		if (error.status === 403) return { kind: 'forbidden', message: MESSAGES.forbidden };
		if (error.status === 404) return { kind: 'notFound', message: MESSAGES.notFound };
		if (error.status === 413 || error.code === 'payload_too_large') {
			return { kind: 'tooLarge', message: MESSAGES.tooLarge };
		}
		if (error.status === 400) return { kind: 'notImage', message: MESSAGES.notImage };
		return { kind: 'server', message: MESSAGES.server };
	}
	return { kind: 'network', message: MESSAGES.network };
}

export class PhotoUploadController {
	state = $state<PhotoUploadState>('idle');
	error = $state<PhotoUploadError | null>(null);
	/** Mirrors the server's `/manage` answer; the UI never derives it locally. */
	canManage = $state(false);

	#listingId: string;

	constructor(listingId: string) {
		this.#listingId = listingId;
	}

	/** Reset to a clean dialog (opening it, or cancelling a failed attempt). */
	reset(): void {
		this.state = 'idle';
		this.error = null;
	}

	/**
	 * Validate, then upload. Resolves `true` on 204; leaves [state] and [error]
	 * populated on failure so the dialog can show the reason and a retry.
	 */
	async upload(file: File): Promise<boolean> {
		const invalid = validatePhotoFile(file);
		if (invalid) {
			this.error = invalid;
			this.state = 'error';
			return false;
		}

		this.error = null;
		this.state = 'uploading';
		try {
			await api.uploadListingImage(this.#listingId, file);
			this.reset();
			return true;
		} catch (error) {
			this.error = mapUploadError(error);
			this.state = 'error';
			return false;
		}
	}
}
