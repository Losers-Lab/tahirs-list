<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLInputAttributes } from 'svelte/elements';

	type Props = {
		label: string;
		/** Helpful idle text (replaced by `error` when present). */
		helper?: string;
		/** Field error below the input; a Snippet lets it carry inline links. */
		error?: string | Snippet;
		/** Renders the label row's trailing adornment (e.g. "Forgot password?"). */
		labelEnd?: Snippet;
		value?: string;
		type?: string;
		name?: string;
		autocomplete?: HTMLInputAttributes['autocomplete'];
		placeholder?: string;
		required?: boolean;
		disabled?: boolean;
		inputmode?: 'text' | 'decimal' | 'email' | 'numeric' | 'tel' | 'url' | 'search' | 'none';
		oninput?: (event: Event) => void;
	};

	let {
		label,
		helper,
		error,
		labelEnd,
		value = $bindable(),
		type = 'text',
		name,
		autocomplete,
		placeholder,
		required,
		disabled,
		inputmode,
		oninput
	}: Props = $props();

	const id = $props.id();
	const hasError = $derived(Boolean(error));
	const describedBy = $derived(hasError ? `${id}-error` : helper ? `${id}-helper` : undefined);
</script>

<div class="space-y-2">
	<div class="flex items-baseline justify-between">
		<label for={id} class="text-body font-medium text-ink-700">{label}</label>
		{#if labelEnd}{@render labelEnd()}{/if}
	</div>

	<input
		{id}
		{type}
		{name}
		{autocomplete}
		{placeholder}
		{required}
		{disabled}
		{inputmode}
		bind:value
		{oninput}
		aria-invalid={hasError || undefined}
		aria-describedby={describedBy}
		class="h-11 w-full rounded-md border bg-ink-0 px-3 text-body text-ink-900 placeholder:text-ink-400 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 {hasError
			? 'border-danger-500'
			: 'border-kraft-300'}"
	/>

	{#if error}
		<p id="{id}-error" role="alert" class="flex items-start gap-1.5 text-small text-danger-600">
			<svg
				aria-hidden="true"
				width="14"
				height="14"
				viewBox="0 0 16 16"
				fill="none"
				stroke="currentColor"
				stroke-width="1.5"
				stroke-linecap="round"
				stroke-linejoin="round"
				class="mt-0.5 h-3.5 w-3.5 shrink-0"
			>
				<circle cx="8" cy="8" r="6.5" />
				<path d="M8 4.75v3.5M8 11.25h.01" />
			</svg>
			<span
				>{#if typeof error === 'string'}{error}{:else}{@render error()}{/if}</span
			>
		</p>
	{:else if helper}
		<p id="{id}-helper" class="text-small text-ink-500">{helper}</p>
	{/if}
</div>
