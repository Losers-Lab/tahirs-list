<script lang="ts">
	import Alert from '#lib/components/auth/Alert.svelte';
	import Button from '#lib/components/auth/Button.svelte';
	import Field from '#lib/components/auth/Field.svelte';
	import {
		createListingSchema,
		type CreateListingFormValues
	} from '#lib/listings/schemas';

	type Props = {
		/** Awaited by the form so its submitting state is driven by the caller. */
		onSubmit: (values: CreateListingFormValues) => Promise<void>;
		submitLabel: string;
		submittingLabel: string;
		serverError: string | null;
		defaultValues?: CreateListingFormValues;
	};

	let { onSubmit, submitLabel, submittingLabel, serverError, defaultValues }: Props = $props();

	const EMPTY_VALUES: CreateListingFormValues = {
		name: '',
		address: '',
		lat: '',
		lng: '',
		cuisine: '',
		isHandCut: false,
		isDelivery: false
	};

	let values = $state<CreateListingFormValues>({ ...EMPTY_VALUES, ...defaultValues });
	let errors = $state<Record<string, string>>({});
	let submitting = $state(false);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		const result = createListingSchema.safeParse(values);
		if (!result.success) {
			const next: Record<string, string> = {};
			for (const issue of result.error.issues) {
				const key = String(issue.path[0] ?? '');
				if (key && !next[key]) next[key] = issue.message;
			}
			errors = next;
			return;
		}
		errors = {};
		submitting = true;
		try {
			await onSubmit(result.data);
		} finally {
			submitting = false;
		}
	}
</script>

<div class="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card sm:p-8">
	{#if serverError}
		<div class="mb-4"><Alert>{serverError}</Alert></div>
	{/if}

	<form onsubmit={submit} novalidate class="space-y-4">
		<Field
			label="Restaurant name"
			error={errors.name}
			bind:value={values.name}
			type="text"
			autocomplete="organization"
			placeholder="e.g. Al-Amir Grill"
			disabled={submitting}
		/>

		<Field
			label="Address"
			error={errors.address}
			bind:value={values.address}
			type="text"
			autocomplete="street-address"
			placeholder="Full street address"
			disabled={submitting}
		/>

		<div class="grid grid-cols-2 gap-4">
			<Field
				label="Latitude"
				helper="e.g. 40.7128"
				error={errors.lat}
				bind:value={values.lat}
				type="text"
				inputmode="decimal"
				autocomplete="off"
				disabled={submitting}
			/>
			<Field
				label="Longitude"
				helper="e.g. -74.0060"
				error={errors.lng}
				bind:value={values.lng}
				type="text"
				inputmode="decimal"
				autocomplete="off"
				disabled={submitting}
			/>
		</div>
		<p class="text-small text-ink-500">
			Coordinates place this listing on the map. Address-to-coordinate lookup is coming in a later
			release.
		</p>

		<Field
			label="Cuisine"
			error={errors.cuisine}
			bind:value={values.cuisine}
			type="text"
			placeholder="e.g. Middle Eastern"
			disabled={submitting}
		/>

		<label class="flex items-start gap-3">
			<input
				type="checkbox"
				bind:checked={values.isHandCut}
				disabled={submitting}
				class="mt-0.5 h-5 w-5 rounded border-kraft-300 text-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
			/>
			<span>
				<span class="font-medium text-ink-900">Hand-cut (Zabiha)</span>
				<span class="block text-small text-ink-500">
					Check if the meat is slaughtered by hand. Leave unchecked if unknown.
				</span>
			</span>
		</label>

		<label class="flex items-start gap-3">
			<input
				type="checkbox"
				bind:checked={values.isDelivery}
				disabled={submitting}
				class="mt-0.5 h-5 w-5 rounded border-kraft-300 text-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
			/>
			<span>
				<span class="font-medium text-ink-900">Offers delivery</span>
				<span class="block text-small text-ink-500">
					Check if the restaurant offers delivery. Leave unchecked if unknown or pickup-only.
				</span>
			</span>
		</label>

		<div class="pt-2">
			<Button type="submit" loading={submitting} loadingLabel={submittingLabel} class="w-full">
				{submitLabel}
			</Button>
		</div>
	</form>
</div>
