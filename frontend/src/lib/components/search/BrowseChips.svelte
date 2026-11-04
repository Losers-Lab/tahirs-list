<script lang="ts">
	import type { BrowseFilter } from '#lib/listings/data';

	type Props = {
		active: BrowseFilter;
		onChange: (filter: BrowseFilter) => void;
	};

	let { active, onChange }: Props = $props();

	const chips: { value: BrowseFilter; label: string }[] = [
		{ value: 'ALL', label: 'All' },
		{ value: 'HAND_CUT', label: 'Hand-cut' },
		{ value: 'DELIVERY', label: 'Delivery' }
	];
</script>

<div role="group" aria-label="Browse filters" class="flex flex-wrap gap-2">
	{#each chips as chip (chip.value)}
		{@const isActive = active === chip.value}
		<button
			type="button"
			aria-pressed={isActive}
			onclick={() => onChange(chip.value)}
			class="inline-flex min-h-11 items-center rounded-full px-5 text-label transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 {isActive
				? 'bg-brand-500 text-cream-50'
				: 'bg-ink-900 text-cream-50 shadow-chip hover:bg-ink-700'}"
		>
			{chip.label}
		</button>
	{/each}
</div>
