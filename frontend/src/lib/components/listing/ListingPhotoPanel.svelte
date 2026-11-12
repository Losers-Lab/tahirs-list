<script lang="ts">
	import type { Snippet } from 'svelte';
	import RestaurantPhoto from './RestaurantPhoto.svelte';
	import PhotoUploadDialog from './PhotoUploadDialog.svelte';
	import { PhotoUploadController } from '#lib/listings/photo-upload.svelte';

	type Props = {
		listingId: string;
		name: string;
		/** Full-res hero (absent when the listing has no photo yet). */
		src?: string;
		srcset?: string;
		fallbackSrc?: string;
		/** Server answer to `/manage` — never derived in the browser. */
		canManage: boolean;
		/** Called after a successful upload so the page can re-read the listing. */
		onUploaded: () => void;
		/** Optional overlay (the verified badge) rendered on the frame. */
		overlay?: Snippet;
	};

	let { listingId, name, src, srcset, fallbackSrc, canManage, onUploaded, overlay }: Props =
		$props();

	const upload = new PhotoUploadController(listingId);

	let dialogOpen = $state(false);
	let trigger = $state<HTMLButtonElement | null>(null);
	let lastFile: File | null = null;
	// The API always returns an image URL, so "has a photo" means "the image
	// actually loads". A new src (fresh upload, or another listing) resets this.
	let photoUnavailable = $state(false);

	$effect(() => {
		void src;
		void fallbackSrc;
		photoUnavailable = false;
	});

	const hasPhoto = $derived(!!(src || fallbackSrc) && !photoUnavailable);
	const controlLabel = $derived(hasPhoto ? 'Replace photo' : 'Add a photo');

	function openDialog() {
		upload.reset();
		dialogOpen = true;
	}

	function closeDialog() {
		dialogOpen = false;
		// Focus returns to the control that opened the dialog.
		trigger?.focus();
	}

	async function choose(file: File) {
		lastFile = file;
		const ok = await upload.upload(file);
		if (ok) {
			dialogOpen = false;
			onUploaded();
		}
	}

	function retry() {
		if (lastFile) void choose(lastFile);
	}
</script>

<div
	class="relative aspect-[16/9] overflow-hidden rounded-lg border border-kraft-200 shadow-card md:aspect-[16/7]"
>
	{#if hasPhoto}
		<RestaurantPhoto
			{src}
			{srcset}
			{fallbackSrc}
			alt={name}
			sizes="(min-width: 1200px) 1200px, 100vw"
			eager
			onUnavailable={() => (photoUnavailable = true)}
		/>
	{:else}
		<div class="absolute inset-0 flex flex-col items-center justify-center gap-2 bg-stamp-50">
			<svg
				aria-hidden="true"
				width="44"
				height="44"
				viewBox="0 0 24 24"
				fill="none"
				stroke="currentColor"
				stroke-width="1.5"
				class="text-ink-400"
			>
				<rect x="3" y="4" width="18" height="16" rx="3" />
				<circle cx="9" cy="10" r="1.6" />
				<path d="m4 18 5-5 4 4 3-3 4 4" />
			</svg>
			<p class="text-small text-ink-500">No photo yet</p>
		</div>
	{/if}

	{#if overlay}
		{@render overlay()}
	{/if}

	{#if canManage}
		<div class="absolute right-3 bottom-3">
			<button
				bind:this={trigger}
				type="button"
				onclick={openDialog}
				class="inline-flex h-10 items-center gap-2 rounded-md bg-ink-900/90 px-4 text-label text-cream-50 shadow-chip backdrop-blur hover:bg-ink-900"
			>
				<svg
					aria-hidden="true"
					width="16"
					height="16"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
				>
					<path d="M12 5v14M5 12h14" />
				</svg>
				{controlLabel}
			</button>
		</div>
	{/if}
</div>

<PhotoUploadDialog
	open={dialogOpen}
	phase={upload.state}
	error={upload.error}
	replacing={hasPhoto}
	onchoose={choose}
	oncancel={closeDialog}
	onretry={retry}
/>

{#if upload.state === 'error' && !dialogOpen}
	<p role="alert" class="mt-2 text-small text-ink-700">{upload.error?.message}</p>
{/if}
