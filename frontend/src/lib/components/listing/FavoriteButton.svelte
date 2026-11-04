<script lang="ts">
	import { favorites } from '#lib/favorites/store.svelte';
	import { sessionState } from '#lib/auth/session.svelte';
	import type { Restaurant } from '#lib/listings/restaurants';

	type Props = {
		listingId: string;
		/** Pass the read-model card so a fresh favourite joins the /saved list. */
		restaurant?: Restaurant;
		variant?: 'card' | 'detail';
		class?: string;
	};

	let { listingId, restaurant, variant = 'detail', class: className = '' }: Props = $props();

	const session = $derived(sessionState.current);
	const loading = $derived(sessionState.restoring || favorites.status === 'loading');
	const favorited = $derived(favorites.isFavorited(listingId));
	const pending = $derived(favorites.isPending(listingId));
	const disabled = $derived(loading || pending);

	const base = $derived(
		variant === 'card'
			? 'inline-flex h-9 w-9 items-center justify-center rounded-full border border-kraft-300 bg-ink-0/90 shadow-chip backdrop-blur transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand-500'
			: 'inline-flex h-11 items-center justify-center gap-2 rounded-md border-[1.5px] px-4 text-label shadow-chip transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500'
	);
	const tone = $derived(
		favorited
			? variant === 'card'
				? ' text-brand-500'
				: ' border-brand-200 bg-brand-50 text-brand-600'
			: variant === 'card'
				? ' text-ink-700 hover:text-brand-500'
				: ' border-kraft-200 bg-ink-0 text-ink-700 hover:border-brand-200 hover:text-brand-600'
	);
	const cls = $derived(`${base}${tone}${disabled ? ' opacity-60' : ''}`);
	const label = $derived(favorited ? 'Remove from favorites' : 'Save to favorites');
</script>

{#snippet heart(filled: boolean)}
	<svg
		aria-hidden="true"
		viewBox="0 0 24 24"
		class="h-5 w-5"
		fill={filled ? 'currentColor' : 'none'}
		stroke="currentColor"
		stroke-width="2"
		stroke-linecap="round"
		stroke-linejoin="round"
	>
		<path
			d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"
		/>
	</svg>
{/snippet}

{#if !session}
	<a href="/login" aria-label="Save to favorites — log in" class="{cls} {className}">
		{@render heart(false)}
		{#if variant === 'detail'}<span>Save</span>{/if}
	</a>
{:else}
	<button
		type="button"
		aria-pressed={favorited}
		aria-label={label}
		title={label}
		{disabled}
		onclick={() => {
			if (disabled) return;
			favorites.clearError();
			void favorites.toggle(listingId, restaurant);
		}}
		class="{cls} {className}"
	>
		{@render heart(favorited)}
		{#if variant === 'detail'}<span>{favorited ? 'Saved' : 'Save'}</span>{/if}
	</button>
	{#if variant === 'detail' && favorites.error}
		<p role="alert" class="text-small text-danger-600">{favorites.error}</p>
	{/if}
{/if}
