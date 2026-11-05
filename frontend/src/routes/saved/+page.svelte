<script lang="ts">
	import SiteShell from '#lib/components/layout/SiteShell.svelte';
	import ListingGrid from '#lib/components/listing/ListingGrid.svelte';
	import SealMark from '#lib/components/trust/SealMark.svelte';
	import { sessionState } from '#lib/auth/session.svelte';
	import { favorites } from '#lib/favorites/store.svelte';

	const session = $derived(sessionState.current);
	const restoring = $derived(sessionState.restoring);
</script>

<SiteShell>
	<header>
		<h1 class="text-title text-ink-900">Saved spots</h1>
		<p class="mt-2 text-body text-ink-500">Restaurants you've favourited, ready to revisit.</p>
	</header>

	<div class="mt-8">
		{#if restoring && !session}
			<div aria-busy="true" class="h-40 animate-pulse rounded-lg bg-ink-100"></div>
		{/if}

		{#if !restoring && !session}
			<div class="rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 p-10 text-center shadow-card">
				<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Signed out" />
				<h2 class="mt-4 text-title text-ink-900">Log in to see your saved spots</h2>
				<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
					Your favourites are tied to your account. Sign in to keep and revisit them.
				</p>
				<a
					href="/login"
					class="mt-6 inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-label text-cream-50 shadow-chip hover:bg-brand-600"
				>
					Log in
				</a>
			</div>
		{/if}

		{#if session && favorites.status === 'loading'}
			<ListingGrid restaurants={[]} status="loading" />
		{/if}

		{#if session && favorites.status === 'error'}
			<div
				role="alert"
				class="rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-8 text-center"
			>
				<h2 class="text-title text-ink-900">We couldn't load your saved spots.</h2>
				<button
					type="button"
					onclick={() => favorites.retry()}
					class="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
				>
					Retry
				</button>
			</div>
		{/if}

		{#if session && favorites.status === 'ready' && favorites.list.length === 0}
			<div class="rounded-lg border border-dashed border-kraft-300 bg-ink-0 p-10 text-center">
				<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
				<h2 class="mt-4 text-title text-ink-900">No saved spots yet</h2>
				<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
					Tap the heart on any restaurant to save it here for later.
				</p>
				<a
					href="/search"
					class="mt-6 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
				>
					Browse restaurants
				</a>
			</div>
		{/if}

		{#if session && favorites.status === 'ready' && favorites.list.length > 0}
			<ListingGrid
				restaurants={favorites.list}
				status="success"
				onRetry={() => favorites.retry()}
			/>
		{/if}
	</div>
</SiteShell>
