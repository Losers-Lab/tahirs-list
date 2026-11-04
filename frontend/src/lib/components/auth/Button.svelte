<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	type Props = HTMLButtonAttributes & {
		variant?: 'primary' | 'ghost';
		loading?: boolean;
		loadingLabel?: string;
		children: Snippet;
	};

	let {
		variant = 'primary',
		loading = false,
		loadingLabel,
		disabled,
		children,
		class: className = '',
		type = 'button',
		...rest
	}: Props = $props();

	const base =
		'inline-flex h-11 items-center justify-center gap-2 rounded-md px-4 text-body font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 disabled:cursor-not-allowed';
	const styles = $derived(
		variant === 'primary'
			? 'bg-brand-500 text-cream-50 shadow-chip hover:bg-brand-600 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 disabled:bg-ink-200 disabled:text-ink-400 disabled:shadow-none'
			: 'text-brand-500 hover:text-brand-600 hover:underline disabled:text-ink-400'
	);
</script>

<button {type} disabled={disabled || loading} class="{base} {styles} {className}" {...rest}>
	{#if loading}
		<svg
			aria-hidden="true"
			class="h-4 w-4 animate-spin motion-reduce:animate-none"
			viewBox="0 0 16 16"
			fill="none"
		>
			<circle class="opacity-25" cx="8" cy="8" r="6" stroke="currentColor" stroke-width="2" />
			<path class="opacity-75" fill="currentColor" d="M8 2a6 6 0 0 1 6 6h-2a4 4 0 0 0-4-4V2Z" />
		</svg>
	{/if}
	{#if loading && loadingLabel}{loadingLabel}{:else}{@render children()}{/if}
</button>
