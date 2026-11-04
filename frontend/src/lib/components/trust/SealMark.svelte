<script lang="ts">
	import type { SVGAttributes } from 'svelte/elements';

	type Props = SVGAttributes<SVGSVGElement> & { srLabel?: string };
	let { srLabel, ...rest }: Props = $props();
</script>

<svg
	aria-hidden={srLabel ? undefined : true}
	viewBox="0 0 24 24"
	fill="none"
	width="24"
	height="24"
	{...rest}
>
	{#if srLabel}<title>{srLabel}</title>{/if}
	<rect x="2" y="2" width="20" height="20" rx="5" fill="currentColor" />
	<path
		d="M7.5 12.5 10.8 15.5 16.8 8.8"
		stroke="var(--color-cream-50)"
		stroke-width="2.4"
		stroke-linecap="round"
		stroke-linejoin="round"
	/>
</svg>
