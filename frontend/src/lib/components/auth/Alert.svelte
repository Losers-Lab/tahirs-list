<script lang="ts">
	import type { Snippet } from 'svelte';

	type Props = {
		variant?: 'banner' | 'inline';
		children: Snippet;
	};

	let { variant = 'banner', children }: Props = $props();

	const styles =
		variant === 'banner'
			? 'border-l-4 border-danger-500 bg-danger-50 text-danger-600'
			: 'border border-danger-100 bg-danger-50 text-danger-600';
</script>

<div
	role="alert"
	tabindex={-1}
	class="flex items-start gap-2 rounded-md px-3 py-2.5 text-small {styles}"
>
	<svg
		aria-hidden="true"
		width="16"
		height="16"
		viewBox="0 0 16 16"
		fill="none"
		stroke="currentColor"
		stroke-width="1.5"
		stroke-linecap="round"
		stroke-linejoin="round"
		class="mt-0.5 h-4 w-4 shrink-0 text-danger-600"
	>
		<circle cx="8" cy="8" r="6.5" />
		<path d="M8 4.75v3.5M8 11.25h.01" />
	</svg>
	<span>{@render children()}</span>
</div>
