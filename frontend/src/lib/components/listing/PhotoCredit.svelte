<script lang="ts">
	/**
	 * SC-231: the credit a photo's source requires.
	 *
	 * It renders nothing when there is no credit — a photo with no attribution is
	 * a backend bug, and inventing a generic "Photo" label would hide it. The text
	 * is small on purpose: credit must be present, not shout over the listing.
	 */
	type Props = {
		attribution?: string;
		licence?: string;
		sourceUrl?: string;
		/** Cards overlay the photo; the detail page shows it under the hero. */
		variant?: 'card' | 'detail';
	};
	let { attribution, licence, sourceUrl, variant = 'detail' }: Props = $props();

	const label = $derived(
		// The source's credit already names its licence ("… CC BY 2.0"), so the enum
		// is only a fallback; printing both reads as a duplicated licence.
		attribution && attribution.length > 0
			? attribution
			: licence && licence.length > 0
				? licence
				: ''
	);

	const classes = $derived(
		variant === 'card'
			? 'absolute right-0 bottom-0 z-10 bg-ink-900/70 px-1.5 py-0.5 text-[10px] leading-tight text-cream-50'
			: 'mt-2 text-caption text-ink-500'
	);
</script>

{#if label}
	<p class={classes}>
		{#if sourceUrl}
			<a
				href={sourceUrl}
				rel="nofollow noopener noreferrer"
				target="_blank"
				class="underline decoration-dotted underline-offset-2 hover:text-ink-700">{label}</a
			>
		{:else}
			{label}
		{/if}
	</p>
{/if}
