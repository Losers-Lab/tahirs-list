<script lang="ts">
	type Props = {
		src?: string;
		fallbackSrc?: string;
		/** Backend multi-width candidates, e.g. "/v1/... 768w, /v1/... 1280w". */
		srcset?: string;
		alt: string;
		sizes: string;
		eager?: boolean;
		/** Called once every candidate has failed, so a caller can show a real empty state. */
		onUnavailable?: () => void;
	};
	let { src, fallbackSrc, srcset, alt, sizes, eager = false, onUnavailable }: Props = $props();

	// Source chain: primary, then fallback, deduped. Advancing on each error lets
	// a 404 swap to the guaranteed thumbnail instead of a blank card; the
	// placeholder appears only when every candidate has failed (or none exist).
	const candidates = $derived(
		[src, fallbackSrc].filter((s): s is string => !!s).filter((s, i, all) => all.indexOf(s) === i)
	);

	let attempt = $state(0);
	// Seed only; the effect below resets it when a different listing's src arrives.
	// svelte-ignore state_referenced_locally
	let prevSrc = $state(src);

	// The retry chain is per-image: a different listing's `src` (client-side nav)
	// resets it so a prior image's failure is not carried over.
	$effect(() => {
		if (src !== prevSrc) {
			prevSrc = src;
			attempt = 0;
		}
	});

	const current = $derived(candidates[attempt]);
	// Only the primary source has the multi-width variants; the guaranteed
	// fallback thumbnail is a single width.
	const responsiveSrcset = $derived(attempt === 0 ? srcset : undefined);

	// Every candidate has failed (or none exist): the listing has no usable photo.
	$effect(() => {
		if (!current && onUnavailable) onUnavailable();
	});
</script>

{#if current}
	<img
		src={current}
		srcset={responsiveSrcset}
		{alt}
		{sizes}
		loading={eager ? 'eager' : 'lazy'}
		style="object-fit: cover;"
		class="absolute inset-0 h-full w-full"
		onerror={() => (attempt += 1)}
	/>
{:else}
	<div class="absolute inset-0 flex items-center justify-center opacity-40">
		<svg
			aria-hidden="true"
			width="48"
			height="48"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="1.5"
			class="text-ink-400"
		>
			<rect x="3" y="3" width="18" height="18" rx="3" />
			<path d="M8 12h8M8 15.5h5" />
		</svg>
	</div>
{/if}
