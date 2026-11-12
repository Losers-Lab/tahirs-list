<script lang="ts">
	import { page } from '$app/state';
	import type { PhotoUploadError, PhotoUploadState } from '#lib/listings/photo-upload.svelte';

	type Props = {
		open: boolean;
		phase: PhotoUploadState;
		error: PhotoUploadError | null;
		/** The listing already has a photo: disclose that it will be destroyed. */
		replacing: boolean;
		onchoose: (file: File) => void;
		oncancel: () => void;
		onretry: () => void;
	};

	let { open, phase, error, replacing, onchoose, oncancel, onretry }: Props = $props();

	let dialogEl = $state<HTMLDialogElement | null>(null);
	let libraryInput = $state<HTMLInputElement | null>(null);
	let cameraInput = $state<HTMLInputElement | null>(null);

	// Native modal: the browser provides the focus trap and Esc handling.
	// jsdom implements neither method, so fall back to the `open` attribute —
	// the dialog's children are in the DOM either way.
	$effect(() => {
		const dialog = dialogEl;
		if (!dialog) return;
		if (open && !dialog.open) {
			if (typeof dialog.showModal === 'function') dialog.showModal();
			else dialog.setAttribute('open', '');
		}
		if (!open && dialog.open) {
			if (typeof dialog.close === 'function') dialog.close();
			else dialog.removeAttribute('open');
		}
	});

	function pick(target: HTMLInputElement | null) {
		target?.click();
	}

	function chosen(event: Event) {
		const input = event.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		input.value = '';
		if (file) onchoose(file);
	}

	/** Esc on a native dialog fires `cancel`; keep the parent state in step. */
	function onNativeCancel(event: Event) {
		event.preventDefault();
		oncancel();
	}

	const busy = $derived(phase === 'uploading');
</script>

<dialog
	bind:this={dialogEl}
	aria-labelledby="photo-dialog-title"
	oncancel={onNativeCancel}
	onclose={() => {
		if (open) oncancel();
	}}
	class="w-[min(92vw,32rem)] rounded-lg border border-kraft-200 bg-ink-0 p-0 text-ink-900 shadow-card backdrop:bg-ink-900/50"
>
	<div class="p-6">
		<h2 id="photo-dialog-title" class="text-heading text-ink-900">
			{replacing ? 'Replace the photo' : 'Add a photo'}
		</h2>

		{#if replacing}
			<p class="mt-2 text-body text-ink-700">
				This replaces the current photo. The previous one cannot be restored.
			</p>
		{:else}
			<p class="mt-2 text-body text-ink-700">
				Show the storefront, the food, or the menu. JPEG or PNG, up to 8 MB.
			</p>
		{/if}

		{#if error}
			<p
				role="alert"
				class="mt-4 rounded-md border border-stamp-200 bg-stamp-50 px-3 py-2 text-body text-ink-900"
			>
				{error.message}
			</p>
			{#if error.kind === 'auth'}
				<a
					href="/login?redirect={encodeURIComponent(page.url.pathname)}"
					class="mt-3 inline-block text-label text-brand-500 underline-offset-2 hover:underline"
				>
					Sign in
				</a>
			{/if}
		{/if}

		<div class="mt-6 flex flex-col gap-2 sm:flex-row">
			<button
				type="button"
				disabled={busy}
				onclick={() => pick(libraryInput)}
				class="inline-flex h-11 flex-1 items-center justify-center rounded-md bg-brand-500 px-5 text-label text-cream-50 shadow-chip hover:bg-brand-600 disabled:cursor-not-allowed disabled:opacity-60"
			>
				Choose a photo
			</button>
			<button
				type="button"
				disabled={busy}
				onclick={() => pick(cameraInput)}
				class="inline-flex h-11 flex-1 items-center justify-center rounded-md border-[1.5px] border-kraft-200 bg-ink-0 px-5 text-label text-ink-700 shadow-chip hover:bg-ink-100 disabled:cursor-not-allowed disabled:opacity-60"
			>
				Take a photo
			</button>
		</div>

		<input
			bind:this={libraryInput}
			type="file"
			accept="image/*"
			class="sr-only"
			aria-hidden="true"
			tabindex="-1"
			onchange={chosen}
		/>
		<input
			bind:this={cameraInput}
			type="file"
			accept="image/*"
			capture="environment"
			class="sr-only"
			aria-hidden="true"
			tabindex="-1"
			onchange={chosen}
		/>

		<div class="mt-4 flex items-center justify-between">
			{#if phase === 'error' && error?.kind !== 'auth'}
				<button
					type="button"
					onclick={onretry}
					class="text-label text-brand-500 underline-offset-2 hover:underline"
				>
					Try again
				</button>
			{:else}
				<span></span>
			{/if}
			<button
				type="button"
				onclick={oncancel}
				class="text-label text-ink-700 underline-offset-2 hover:underline"
			>
				Cancel
			</button>
		</div>

		{#if busy}
			<p aria-live="polite" class="mt-3 text-small text-ink-500">Uploading…</p>
		{/if}
	</div>
</dialog>
