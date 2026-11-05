<script lang="ts">
	import { goto } from '$app/navigation';

	type Props = { defaultValue?: string; class?: string };
	let { defaultValue = '', class: className = '' }: Props = $props();

	// The input is intentionally seeded once; later URL changes flow through the
	// `defaultValue` prop on navigation, not through this state.
	// svelte-ignore state_referenced_locally
	let value = $state(defaultValue);

	function submit(event: SubmitEvent) {
		event.preventDefault();
		const q = value.trim();
		void goto(q ? `/search?q=${encodeURIComponent(q)}` : '/search');
	}
</script>

<form role="search" onsubmit={submit} class="flex flex-col gap-3 sm:flex-row {className}">
	<label for="site-search" class="sr-only">Search restaurants, dishes, or area</label>
	<input
		id="site-search"
		type="search"
		bind:value
		placeholder="Search restaurants, dishes, or area…"
		autocomplete="off"
		class="h-14 w-full rounded-md border-[1.5px] border-kraft-300 bg-ink-0 px-4 text-body text-ink-900 shadow-card placeholder:text-ink-500 focus-visible:border-brand-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
	/>
	<button
		type="submit"
		class="inline-flex h-14 items-center justify-center gap-2 rounded-md bg-brand-500 px-6 text-label text-cream-50 shadow-chip hover:bg-brand-600 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0"
	>
		<svg
			aria-hidden="true"
			width="18"
			height="18"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="2"
			stroke-linecap="round"
		>
			<circle cx="11" cy="11" r="7" />
			<path d="m20 20-3.8-3.8" />
		</svg>
		Search
	</button>
</form>
