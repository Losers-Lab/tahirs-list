<script lang="ts">
	import { afterNavigate } from '$app/navigation';
	import Wordmark from '#lib/components/brand/Wordmark.svelte';
	import TahirHeadIcon from '#lib/components/brand/TahirHeadIcon.svelte';
	import AuthHeader from '#lib/components/auth/AuthHeader.svelte';

	let open = $state(false);

	const secondaryLinks = [
		{ href: '/search', label: 'Search' },
		{ href: '/add-listing', label: 'Add a restaurant' }
	];

	// The mobile panel is a navigation surface: it closes after every navigation
	// (search-browse.md §Header) so it never covers the page it navigated to.
	afterNavigate(() => {
		open = false;
	});

	function onKeydown(event: KeyboardEvent) {
		if (event.key === 'Escape') open = false;
	}
</script>

<svelte:window onkeydown={onKeydown} />

<header class="sticky top-0 z-30 border-b-[1.5px] border-kraft-200 bg-cream-50">
	<div class="mx-auto flex h-16 max-w-[1200px] items-center justify-between gap-4 px-5">
		<a href="/" class="inline-flex items-center gap-2.5 hover:opacity-90">
			<TahirHeadIcon class="h-10 w-10 md:h-14 md:w-14" />
			<span class="flex flex-col justify-center leading-none">
				<Wordmark class="text-title" />
				<span class="mt-1 hidden text-small text-ink-500 md:block">find halal food</span>
			</span>
		</a>
		<div class="hidden items-center gap-3 md:flex">
			{#each secondaryLinks as link (link.href)}
				<a href={link.href} class="text-small text-ink-700 hover:text-brand-600">{link.label}</a>
			{/each}
			<AuthHeader />
		</div>
		<div class="md:hidden">
			<button
				type="button"
				aria-expanded={open}
				aria-controls="mobile-nav"
				aria-label={open ? 'Close menu' : 'Open menu'}
				onclick={() => (open = !open)}
				class="inline-flex h-11 w-11 items-center justify-center text-ink-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
			>
				<svg
					aria-hidden="true"
					width="22"
					height="22"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
				>
					{#if open}
						<path d="M6 6l12 12M18 6L6 18" />
					{:else}
						<path d="M4 7h16M4 12h16M4 17h16" />
					{/if}
				</svg>
			</button>
			{#if open}
				<div
					id="mobile-nav"
					class="absolute top-16 right-0 left-0 z-40 border-b-[1.5px] border-kraft-200 bg-cream-50 px-5 py-3 shadow-pop"
				>
					<nav class="flex flex-col">
						{#each secondaryLinks as link (link.href)}
							<a
								href={link.href}
								onclick={() => (open = false)}
								class="flex min-h-11 items-center border-t border-kraft-100 px-1 py-2 text-body text-ink-700"
							>
								{link.label}
							</a>
						{/each}
						<!-- Signed-out visitors (and owners) reach auth from here too; the
						     AuthHeader is otherwise desktop-only. -->
						<div class="mt-2 border-t border-kraft-100 pt-3">
							<AuthHeader />
						</div>
					</nav>
				</div>
			{/if}
		</div>
	</div>
</header>
