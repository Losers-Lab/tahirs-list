<script lang="ts">
	import { sessionState, signOut } from '#lib/auth/session.svelte';
</script>

{#if sessionState.restoring && !sessionState.current}
	<div aria-busy="true" class="h-8 w-56 animate-pulse rounded bg-ink-100"></div>
{:else if sessionState.current}
	<div class="flex items-center gap-3 text-ink-500">
		<a
			href="/add-listing"
			class="rounded-md bg-brand-500 px-3 py-1.5 text-small font-medium text-cream-50 shadow-chip hover:bg-brand-600"
		>
			Add a listing
		</a>
		<span class="text-small">
			Signed in as <strong class="text-ink-700">{sessionState.current.email}</strong>
		</span>
		<button
			type="button"
			onclick={() => signOut()}
			class="rounded-md px-2 py-1 text-small text-brand-500 hover:bg-ink-100 hover:text-brand-600"
		>
			Sign out
		</button>
	</div>
{:else}
	<div class="flex items-center gap-2">
		<a href="/login" class="rounded-md px-2 py-1 text-small text-ink-700 hover:text-brand-600">
			Log in
		</a>
		<a
			href="/signup"
			class="rounded-md bg-brand-500 px-3 py-1.5 text-small font-medium text-cream-50 shadow-chip hover:bg-brand-600"
		>
			Sign up
		</a>
	</div>
{/if}
