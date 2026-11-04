<script lang="ts">
	type Props = { lat: number; lng: number; restaurantName: string };
	let { lat, lng, restaurantName }: Props = $props();

	const valid = $derived(Number.isFinite(lat) && Number.isFinite(lng));
</script>

{#if valid}
	<div class="overflow-hidden rounded-lg border border-kraft-200 shadow-card">
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
