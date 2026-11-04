<script lang="ts">
	import ListingCard from './ListingCard.svelte';
	import SealMark from '#lib/components/trust/SealMark.svelte';
	import type { Restaurant } from '#lib/listings/restaurants';

	type Props = {
		restaurants: Restaurant[];
		status: 'loading' | 'success' | 'error';
		onRetry?: () => void;
		emptyHref?: string;
	};

	let { restaurants, status, onRetry, emptyHref = '/search' }: Props = $props();

	const skeletonKeys = Array.from({ length: 8 }, (_, index) => index);
</script>

{#if status === 'loading'}
	<div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
		{#each skeletonKeys as key (key)}
			<div class="overflow-hidden rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 shadow-card">
				<div class="aspect-video bg-ink-100"></div>
				<div class="space-y-3 p-5">
					<div class="h-4 w-3/4 rounded bg-ink-100"></div>
					<div class="h-3 w-1/2 rounded bg-ink-100"></div>
				</div>
			</div>
		{/each}
	</div>
{:else if status === 'error'}
	<div
		role="alert"
		class="mb-5 flex items-start gap-2 border border-danger-100 bg-danger-50 px-3 py-3 text-small text-danger-600"
	>
		<svg
			aria-hidden="true"
			width="16"
			height="16"
			viewBox="0 0 16 16"
			fill="none"
			stroke="currentColor"
			stroke-width="1.5"
			stroke-linecap="round"
			stroke-linejoin="round"
			class="mt-0.5 h-4 w-4 shrink-0"
		>
			<path d="M8 2.5 14.5 13h-13L8 2.5Z" />
			<path d="M8 6.5v3M8 11.5h.01" />
		</svg>
		<span class="flex flex-1 flex-wrap items-center gap-2">
			<span>We couldn't load results.</span>
			{#if onRetry}
				<button
					type="button"
					onclick={onRetry}
					class="rounded-full bg-ink-900 px-3 py-1 text-small font-medium text-cream-50 shadow-chip hover:bg-ink-700"
				>
					Retry
				</button>
			{/if}
		</span>
	</div>
	<div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4"></div>
{:else if restaurants.length === 0}
	<div class="rounded-lg border border-dashed border-kraft-300 bg-ink-0 p-10 text-center">
		<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
		<h2 class="mt-4 text-title text-ink-900">Nothing matches yet</h2>
		<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
			Try a different search — or add the spot you know.
		</p>
		<a
			href={emptyHref}
			class="mt-6 inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
		>
			Add a restaurant
		</a>
	</div>
{:else}
	<div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
		{#each restaurants as r (r.id)}
			<ListingCard restaurant={r} />
		{/each}
	</div>
{/if}
