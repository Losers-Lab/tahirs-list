<script lang="ts">
	import SiteShell from '#lib/components/layout/SiteShell.svelte';
	import SearchBar from '#lib/components/search/SearchBar.svelte';
	import BrowseChips from '#lib/components/search/BrowseChips.svelte';
	import ListingGrid from '#lib/components/listing/ListingGrid.svelte';
	import SealMark from '#lib/components/trust/SealMark.svelte';
	import { ListingsController } from '#lib/listings/controller.svelte';
	import type { BrowseFilter } from '#lib/listings/data';
	import type { PageData } from './$types';

	let { data }: { data?: PageData } = $props();
	let filter = $state<BrowseFilter>('ALL');
	// Seed from the SSR load so the first client render reuses the server data.
	const listings = new ListingsController(data?.listings ?? null, { query: '', filter: 'ALL' });

	$effect(() => {
		void listings.load('', filter);
	});
</script>

<SiteShell>
	<section class="text-center">
		<SealMark class="mx-auto h-10 w-10 text-brand-500" srLabel="Tahir's List seal" />
		<h1 class="mx-auto mt-4 max-w-[640px] text-display text-ink-900">
			Find halal food near you. Stamped &amp; trusted.
		</h1>
		<p class="mx-auto mt-3 max-w-[56ch] text-body text-ink-500">
			Real certifications, reviewed by people — not just a label.
		</p>

		<div class="mx-auto mt-8 max-w-[720px] text-left">
			<SearchBar />
		</div>

		<div class="mx-auto mt-6 max-w-[720px]">
			<BrowseChips active={filter} onChange={(value) => (filter = value)} />
		</div>
	</section>

	<section class="mt-12" aria-live="polite">
		<div class="mb-5 flex items-baseline justify-between">
			<p class="text-body text-ink-500" data-testid="result-count">
				{#if listings.status === 'success' && listings.restaurants.length > 0}
					{listings.verifiedCount} verified · {listings.restaurants.length} spots near you
				{:else}
					Finding halal spots…
				{/if}
			</p>
		</div>
		<ListingGrid
			restaurants={listings.restaurants}
			status={listings.status}
			onRetry={() => void listings.retry('', filter)}
			emptyHref="/add-listing"
		/>
	</section>
</SiteShell>
