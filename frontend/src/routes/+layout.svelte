<script lang="ts">
	import '../app.css';
	import { onMount, untrack } from 'svelte';
	import type { Snippet } from 'svelte';
	import { restore, sessionState } from '#lib/auth/session.svelte';
	import { favorites } from '#lib/favorites/store.svelte';

	let { children }: { children: Snippet } = $props();

	// Re-materialize a persisted session once, in the browser only. The
	// server-rendered snapshot is always anonymous (see session.svelte.ts).
	onMount(() => {
		restore();
	});

	// Keep the shared favorites store in sync with the signed-in account. Runs
	// in the browser; on the server no effect runs, so nothing is fetched.
	$effect(() => {
		const accountId = sessionState.current?.accountId ?? null;
		// syncAccount performs an async fetch and assigns store state; the effect
		// only tracks the account switch itself, not the store's internals.
		untrack(() => favorites.syncAccount(accountId));
	});
</script>

<svelte:head>
	<title>Tahir's List</title>
	<meta
		name="description"
		content="Find halal food near you — hand-cut or machine-cut, verified and trusted."
	/>
</svelte:head>

{@render children()}
