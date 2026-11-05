<script lang="ts">
	import { page } from '$app/state';
	import SiteShell from '#lib/components/layout/SiteShell.svelte';
	import SealMark from '#lib/components/trust/SealMark.svelte';

	const notFound = $derived(page.status === 404);
</script>

<SiteShell mainClass="mx-auto flex max-w-[1200px] items-center px-5 py-16 pb-32 lg:py-24 lg:pb-16">
	<div class="mx-auto w-full max-w-md text-center">
		<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Tahir's List seal" />
		<h1 class="mt-4 text-title text-ink-900">
			{notFound ? 'Page not found' : 'Something went wrong'}
		</h1>
		<p class="mt-2 text-body text-ink-500">
			{notFound
				? "We couldn't find that page. It may have moved or the link is out of date."
				: 'Something went wrong on our end. Please try again.'}
		</p>
		<a
			href="/"
			class="mt-6 inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-label text-cream-50 shadow-chip hover:bg-brand-600"
		>
			Back to home
		</a>
	</div>
</SiteShell>
