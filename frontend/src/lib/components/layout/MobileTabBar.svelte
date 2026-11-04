<script lang="ts">
	import { page } from '$app/state';

	type Props = { pathname?: string };
	let { pathname = page.url.pathname }: Props = $props();

	type Tab = { label: string; href: string };

	const tabs: Tab[] = [
		{ label: 'Search', href: '/search' },
		{ label: 'Add', href: '/add-listing' },
		{ label: 'Saved', href: '/saved' },
		{ label: 'Account', href: '/account' }
	];

	function isActive(href: string): boolean {
		return href === '/search' ? pathname === '/search' || pathname === '/' : pathname === href;
	}
</script>

<nav
	aria-label="Primary"
	class="fixed inset-x-0 bottom-0 z-20 border-t border-kraft-300 bg-ink-0 pb-[env(safe-area-inset-bottom)] lg:hidden"
>
	<div class="grid h-14 grid-cols-4">
		{#each tabs as tab (tab.href)}
			{@const active = isActive(tab.href)}
			<a
				href={tab.href}
				aria-current={active ? 'page' : undefined}
				class="flex min-h-11 flex-col items-center justify-center gap-0.5 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand-500 {active
					? 'text-brand-500'
					: 'text-ink-500'}"
			>
				{#if tab.href === '/search'}
					<svg
						aria-hidden="true"
						width="24"
						height="24"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
					>
						<circle cx="11" cy="11" r="7" />
						<path d="m20 20-3.8-3.8" />
					</svg>
				{:else if tab.href === '/add-listing'}
					<svg
						aria-hidden="true"
						width="24"
						height="24"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
					>
						<rect x="4" y="4" width="16" height="16" rx="3" />
						<path d="M12 9v6M9 12h6" />
					</svg>
				{:else if tab.href === '/saved'}
					<svg
						aria-hidden="true"
						width="24"
						height="24"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						stroke-linejoin="round"
					>
						<path d="M6 4h12v16l-6-4-6 4Z" />
					</svg>
				{:else}
					<svg
						aria-hidden="true"
						width="24"
						height="24"
						viewBox="0 0 24 24"
						fill="none"
						stroke="currentColor"
						stroke-width="2"
						stroke-linecap="round"
					>
						<circle cx="12" cy="8" r="4" />
						<path d="M4 20a8 8 0 0 1 16 0" />
					</svg>
				{/if}
				<span class="text-small">{tab.label}</span>
				<span
					aria-hidden="true"
					class="h-[3px] w-8 rounded-full {active ? 'bg-brand-500' : 'bg-transparent'}"
				></span>
			</a>
		{/each}
	</div>
</nav>
