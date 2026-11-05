<script lang="ts">
	import { page } from '$app/state';
	import SiteShell from '#lib/components/layout/SiteShell.svelte';
	import SearchBar from '#lib/components/search/SearchBar.svelte';
	import BrowseChips from '#lib/components/search/BrowseChips.svelte';
	import ListingGrid from '#lib/components/listing/ListingGrid.svelte';
	import { ListingsController } from '#lib/listings/controller.svelte';
	import type { BrowseFilter } from '#lib/listings/data';
	import type { PageData } from './$types';

	let { data }: { data?: PageData } = $props();
	const q = $derived(page.url.searchParams.get('q') ?? '');
	let filter = $state<BrowseFilter>('ALL');
	// Seed from the SSR load (already text-filtered by the server).
	const listings = new ListingsController(data?.listings ?? null, { query: q, filter: 'ALL' });

	// Adopt fresh server data on client-side `?q=` navigation; runs before the
	// load effect so the matching client fetch is skipped.
	$effect(() => {
		if (data?.listings) listings.adopt(data.listings, { query: q, filter: 'ALL' });
	});

	$effect(() => {
		void listings.load(q, filter);
	});
</script>

<SiteShell>
	<section class="text-center">
		<h1 class="text-title text-ink-900">
			{#if q}
				Results for <span class="text-brand-500">“{q}”</span>
			{:else}
				Browse halal food
			{/if}
		</h1>

		<div class="mx-auto mt-6 max-w-[720px] text-left">
			<SearchBar defaultValue={q} />
		</div>

		<div class="mx-auto mt-6 max-w-[720px]">
			<BrowseChips active={filter} onChange={(value) => (filter = value)} />
		</div>
	</section>

	<section class="mt-10 border-t-[1.5px] border-dashed border-kraft-300 pt-8" aria-live="polite">
		<p class="mb-5 text-body text-ink-500" data-testid="result-count">
			{#if listings.status === 'success' && listings.restaurants.length > 0}
				{#if listings.verifiedCount > 0}
					{listings.verifiedCount} verified · {listings.restaurants.length} spots near you
				{:else}
					{listings.restaurants.length} spots near you
				{/if}
			{:else if listings.status === 'loading'}
				Finding halal spots…
			{/if}
		</p>
		<ListingGrid
			restaurants={listings.restaurants}
			status={listings.status}
			onRetry={() => void listings.retry(q, filter)}
			emptyHref="/add-listing"
		/>
	</section>
</SiteShell>
