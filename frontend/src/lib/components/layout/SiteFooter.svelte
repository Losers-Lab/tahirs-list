<script lang="ts">
	import Wordmark from '#lib/components/brand/Wordmark.svelte';
</script>

<footer class="mt-16 border-t-[1.5px] border-kraft-200 bg-cream-50">
	<div
		class="mx-auto flex max-w-[1200px] flex-col gap-4 px-5 py-8 text-small text-ink-500 sm:flex-row sm:items-center sm:justify-between"
	>
		<span><Wordmark class="text-heading" /> · find halal food</span>
		<nav aria-label="Footer" class="flex flex-wrap gap-4">
			<a href="/how-verification-works" class="hover:text-brand-600">How verification works</a>
			<a href="/add-listing" class="hover:text-brand-600">Add a restaurant</a>
			<a href="/about" class="hover:text-brand-600">About</a>
		</nav>
	</div>
</footer>
