<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLInputAttributes } from 'svelte/elements';

	type Props = {
		label: string;
		helper?: string;
		error?: string;
		labelEnd?: Snippet;
		value?: string;
		name?: string;
		autocomplete?: HTMLInputAttributes['autocomplete'];
		placeholder?: string;
		required?: boolean;
		disabled?: boolean;
		oninput?: (event: Event) => void;
	};

	let {
		label,
		helper,
		error,
		labelEnd,
		value = $bindable(),
		name,
		autocomplete,
		placeholder,
		required,
		disabled,
		oninput
	}: Props = $props();

	const id = $props.id();
	let visible = $state(false);

	const hasError = $derived(Boolean(error));
	const describedBy = $derived(hasError ? `${id}-error` : helper ? `${id}-helper` : undefined);
</script>

<div class="space-y-2">
	<div class="flex items-baseline justify-between">
		<label for={id} class="text-body font-medium text-ink-700">{label}</label>
		{#if labelEnd}{@render labelEnd()}{/if}
	</div>

	<div class="relative">
		<input
			{id}
			{name}
			{autocomplete}
			{placeholder}
			{required}
			{disabled}
			{oninput}
			bind:value
			type={visible ? 'text' : 'password'}
			aria-invalid={hasError || undefined}
			aria-describedby={describedBy}
			class="h-11 w-full rounded-md border bg-ink-0 px-3 pr-14 text-body text-ink-900 placeholder:text-ink-400 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 {hasError
				? 'border-danger-500'
				: 'border-kraft-300'}"
		/>
		<button
			type="button"
			aria-label={visible ? 'Hide password' : 'Show password'}
			aria-pressed={visible}
			onclick={() => (visible = !visible)}
			class="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-ink-400 hover:text-ink-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
		>
			{#if visible}
				<svg
					aria-hidden="true"
					width="18"
					height="18"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="1.8"
					stroke-linecap="round"
					stroke-linejoin="round"
				>
					<path d="M3 3l18 18" />
					<path
						d="M10.6 5.6A9.8 9.8 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17.9 17.9 0 0 1-2.4 3.2M6.1 6.1A17.6 17.6 0 0 0 2.5 12s3.5 6.5 9.5 6.5a9.6 9.6 0 0 0 3.4-.6M9.9 9.9a2.5 2.5 0 0 0 3.4 3.4"
					/>
				</svg>
			{:else}
				<svg
					aria-hidden="true"
					width="18"
					height="18"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="1.8"
					stroke-linecap="round"
					stroke-linejoin="round"
				>
					<path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
					<circle cx="12" cy="12" r="2.5" />
				</svg>
			{/if}
		</button>
	</div>

	{#if hasError}
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
			<span>{error}</span>
		</p>
	{:else if helper}
		<p id="{id}-helper" class="text-small text-ink-500">{helper}</p>
	{/if}
</div>
