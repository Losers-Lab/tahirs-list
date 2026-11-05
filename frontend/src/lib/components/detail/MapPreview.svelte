<script lang="ts">
	type Props = { lat: number; lng: number; restaurantName: string };
	let { lat, lng, restaurantName }: Props = $props();

	const valid = $derived(Number.isFinite(lat) && Number.isFinite(lng));

	// A cross-origin <iframe> becomes `document.activeElement` when focus enters
	// the frame, but never matches `:focus` / `:focus-within`, so CSS alone cannot
	// draw a keyboard ring for the map. The window blurs at that moment, and
	// regains focus when the keyboard user leaves the frame again.
	let mapFocused = $state(false);

	function onWindowBlur() {
		if (document.activeElement instanceof HTMLIFrameElement) mapFocused = true;
	}

	function onWindowFocus() {
		mapFocused = false;
	}
</script>

<svelte:window onblur={onWindowBlur} onfocus={onWindowFocus} />

{#if valid}
	<div
		class="overflow-hidden rounded-lg border border-kraft-200 shadow-card {mapFocused
			? 'outline-2 outline-offset-2 outline-brand-500'
			: ''}"
	>
		<iframe
			title="Map showing the location of {restaurantName}"
			src="https://www.google.com/maps?q={lat},{lng}&z=16&output=embed"
			class="block h-64 w-full border-0"
			loading="lazy"
			referrerpolicy="no-referrer-when-downgrade"
			allowfullscreen
		></iframe>
	</div>
{/if}
