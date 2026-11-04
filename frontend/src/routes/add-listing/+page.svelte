<script lang="ts">
	import Alert from '#lib/components/auth/Alert.svelte';
	import Button from '#lib/components/auth/Button.svelte';
	import ListingForm from '#lib/components/listing/ListingForm.svelte';
	import UnverifiedTag from '#lib/components/trust/UnverifiedTag.svelte';
	import { api, ApiError, type ListingResponse } from '#lib/api/client';
	import { sessionState } from '#lib/auth/session.svelte';
	import type { CreateListingFormValues } from '#lib/listings/schemas';

	const session = $derived(sessionState.current);
	const restoring = $derived(sessionState.restoring);

	let created = $state<ListingResponse | null>(null);
	let serverError = $state<string | null>(null);
	let formKey = $state(0);

	async function onSubmit(values: CreateListingFormValues) {
		serverError = null;
		try {
			const listing = await api.createListing({
				name: values.name,
				address: values.address,
				lat: Number(values.lat),
				lng: Number(values.lng),
				cuisine: values.cuisine,
				// Tri-state flags: omit when unchecked → the backend records
				// unknown (null); checked → claimed.
				...(values.isHandCut ? { isHandCut: true } : {}),
				...(values.isDelivery ? { isDelivery: true } : {})
			});
			created = listing;
		} catch (error) {
			if (error instanceof ApiError) {
				if (error.code === 'invalid_input' && error.detail) {
					serverError = error.detail;
					return;
				}
				if (error.status === 401) {
					serverError = 'Your session has expired. Please log in again to add a listing.';
					return;
				}
			}
			serverError = 'Something went wrong. Please try again.';
		}
	}
</script>

{#if restoring && !session}
	<main class="mx-auto max-w-lg px-5 py-12">
		<div aria-busy="true" class="h-40 animate-pulse rounded-lg bg-ink-100"></div>
	</main>
{:else if !session}
	<main class="mx-auto max-w-lg px-5 py-12 text-center">
		<h1 class="text-title text-ink-900">Add a restaurant</h1>
		<p class="mt-2 text-body text-ink-500">You need to be signed in to add a listing.</p>
		<div class="mt-6">
			<a
				href="/login"
				class="inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
			>
				Log in to continue
			</a>
		</div>
	</main>
{:else if created}
	<main class="mx-auto max-w-lg px-5 py-12">
		<div class="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card">
			<div class="flex items-center justify-between gap-3">
				<h1 class="text-title text-ink-900">Listing saved</h1>
				<UnverifiedTag />
			</div>
			<p class="mt-3 text-body text-ink-500">
				<span class="font-medium text-ink-900">{created.name}</span> is now on Tahir's List. This
				listing is currently <span class="font-medium text-ink-700">unverified</span> — the owner
				can claim it and submit their halal certification for review.
			</p>
			<div class="mt-6 flex flex-wrap items-center gap-3">
				<a
					href="/restaurants/{encodeURIComponent(created.id)}/edit"
					class="inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
				>
					Edit listing
				</a>
				<Button
					type="button"
					variant="ghost"
					onclick={() => {
						created = null;
						serverError = null;
						formKey += 1;
					}}
				>
					Add another listing
				</Button>
				<a
					href="/"
					class="rounded-md px-2 py-1 text-small text-brand-500 hover:text-brand-600 hover:underline"
				>
					Back to home
				</a>
			</div>
		</div>
	</main>
{:else}
	<main class="mx-auto max-w-lg px-5 py-12">
		<div class="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card sm:p-8">
			<h1 class="text-title text-ink-900">Add a restaurant</h1>
			<p class="mt-2 text-body text-ink-500">
				Share a halal place you know. Listings start <span class="font-medium text-ink-700"
					>unverified</span
				> and can be claimed by the owner later.
			</p>

			{#if serverError}
				<div class="mt-4"><Alert>{serverError}</Alert></div>
			{/if}

			<div class="mt-6">
				{#key formKey}
					<ListingForm
						{onSubmit}
						submitLabel="Add listing"
						submittingLabel="Saving listing…"
						{serverError}
					/>
				{/key}
			</div>
		</div>
	</main>
{/if}
