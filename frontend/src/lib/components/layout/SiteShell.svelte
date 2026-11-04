<script lang="ts">
	import type { Snippet } from 'svelte';
	import SiteHeader from './SiteHeader.svelte';
	import SiteFooter from './SiteFooter.svelte';
	import MobileTabBar from './MobileTabBar.svelte';

	type Props = {
		children: Snippet;
		mainClass?: string;
	};

	let { children, mainClass = 'mx-auto max-w-[1200px] px-5 py-12 pb-32 lg:pb-12' }: Props =
		$props();
</script>

<div class="min-h-screen bg-cream-50">
	<SiteHeader />
	<main class={mainClass}>
		{@render children()}
	</main>
	<SiteFooter />
	<MobileTabBar />
</div>
