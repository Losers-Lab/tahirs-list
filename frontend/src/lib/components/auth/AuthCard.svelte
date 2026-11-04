<script lang="ts">
	import type { Snippet } from 'svelte';
	import Wordmark from '#lib/components/brand/Wordmark.svelte';
	import TahirHeadIcon from '#lib/components/brand/TahirHeadIcon.svelte';

	type Props = {
		heading: string;
		subcopy: string;
		footer: Snippet;
		trustLine?: boolean;
		children: Snippet;
	};

	let { heading, subcopy, footer, trustLine = false, children }: Props = $props();
</script>

<div class="flex min-h-screen flex-col items-center justify-center bg-cream-50 px-0 py-0 sm:px-6 sm:py-10">
	<div class="w-full max-w-[400px]">
		<div
			class="rounded-none border-0 bg-ink-0 px-5 py-8 shadow-none sm:rounded-lg sm:border sm:border-kraft-200 sm:px-8 sm:py-8 sm:shadow-card"
		>
			<div class="mb-6">
				<a href="/" class="inline-flex items-center gap-2 hover:opacity-90">
					<TahirHeadIcon class="h-8 w-8" />
					<Wordmark class="text-heading" />
				</a>
			</div>

			<header class="space-y-1.5">
				<h1 class="text-title text-ink-900">{heading}</h1>
				<p class="text-body text-ink-500">{subcopy}</p>
			</header>

			<div class="mt-6">{@render children()}</div>
		</div>

		<footer class="px-5 pb-8 pt-4 text-center text-body text-ink-500 sm:px-0">
			{@render footer()}
		</footer>

		{#if trustLine}
			<p class="px-5 pb-8 text-center text-small text-ink-500 sm:px-0">
				By signing up you agree to our
				<a href="#" class="text-brand-500 hover:text-brand-600 hover:underline">Terms</a>
				and
				<a href="#" class="text-brand-500 hover:text-brand-600 hover:underline">Privacy Policy</a>.
			</p>
		{/if}
	</div>
</div>
