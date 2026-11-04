<script lang="ts">
	import { page } from '$app/state';
	import ListingForm from '#lib/components/listing/ListingForm.svelte';
	import VerifiedBadge from '#lib/components/trust/VerifiedBadge.svelte';
	import UnverifiedTag from '#lib/components/trust/UnverifiedTag.svelte';
	import { api, ApiError, type ListingResponse } from '#lib/api/client';
	import { sessionState } from '#lib/auth/session.svelte';
	import { getRestaurant } from '#lib/listings/data';
	import type { CreateListingFormValues } from '#lib/listings/schemas';
	import type { Restaurant } from '#lib/listings/restaurants';
	import { verificationStatus } from '#lib/listings/restaurants';

	const session = $derived(sessionState.current);
	const restoring = $derived(sessionState.restoring);

	let restaurant = $state<Restaurant | null>(null);
	let status = $state<'loading' | 'success' | 'error' | 'not-found'>('loading');
	let reload = $state(0);
	let generation = 0;
	let saved = $state<ListingResponse | null>(null);
	let serverError = $state<string | null>(null);

	async function load(id: string) {
		const gen = ++generation;
		status = 'loading';
		try {
			const result = await getRestaurant(id);
			if (gen !== generation) return;
			restaurant = result ?? null;
			status = result ? 'success' : 'not-found';
		} catch {
			if (gen === generation) status = 'error';
		}
	}

	$effect(() => {
		const id = page.params.id ?? '';
		const accountId = session?.accountId ?? null;
		void reload;
		// Never fetch while anonymous/restoring: the read is only useful to a
		// signed-in owner, and the legacy screen deliberately made no request.
		if (!accountId) return;
		void load(id);
	});

	async function onSubmit(values: CreateListingFormValues) {
		serverError = null;
		const id = page.params.id ?? '';
		try {
			const updated = await api.updateListing(id, {
				name: values.name,
				address: values.address,
				lat: Number(values.lat),
				lng: Number(values.lng),
				cuisine: values.cuisine,
				...(values.isHandCut ? { isHandCut: true } : {}),
				...(values.isDelivery ? { isDelivery: true } : {})
			});
			saved = updated;
		} catch (error) {
			if (error instanceof ApiError) {
				if (error.code === 'invalid_input' && error.detail) {
					serverError = error.detail;
					return;
				}
				if (error.code === 'not_listing_owner' || error.status === 403) {
					serverError =
						'You can only edit a listing you own. This listing belongs to a different account.';
					return;
				}
				if (error.status === 401) {
					serverError = 'Your session has expired. Please log in again to save changes.';
					return;
				}
				if (error.status === 404) {
					serverError = 'This listing was not found. It may have been removed.';
					return;
				}
			}
			serverError = 'Something went wrong. Please try again.';
		}
	}

	function toFormValues(r: Restaurant): CreateListingFormValues {
		return {
			name: r.name,
			address: r.address,
			lat: String(r.lat),
			lng: String(r.lng),
			cuisine: r.cuisine,
			isHandCut: r.isHandCut === true,
			isDelivery: r.isDelivery === true
		};
	}

	const verified = $derived(restaurant ? verificationStatus(restaurant) === 'VERIFIED' : false);
</script>

{#if restoring && !session}
	<main class="mx-auto max-w-lg px-5 py-12">
		<div aria-busy="true" class="h-40 animate-pulse rounded-lg bg-ink-100"></div>
	</main>
{:else if !session}
	<main class="mx-auto max-w-lg px-5 py-12 text-center">
		<h1 class="text-title text-ink-900">Edit a listing</h1>
		<p class="mt-2 text-body text-ink-500">You need to be signed in to edit a listing.</p>
		<div class="mt-6">
			<a
				href="/login"
				class="inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
			>
				Log in to continue
			</a>
		</div>
	</main>
{:else}
	<main class="mx-auto max-w-lg px-5 py-12">
		<p class="mb-4 text-small text-ink-500">
			<a
				href="/restaurants/{encodeURIComponent(page.params.id ?? '')}"
				class="underline-offset-2 hover:text-brand-600 hover:underline"
			>
				← Back to listing
			</a>
		</p>

		{#if status === 'loading'}
			<div aria-busy="true" class="h-40 animate-pulse rounded-lg bg-ink-100"></div>
		{:else if status === 'error'}
			<div
				role="alert"
				class="rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-8 text-center"
			>
				<h2 class="text-title text-ink-900">We couldn't load this listing.</h2>
				<p class="mt-2 text-body text-ink-500">Something went wrong on our end. Try again.</p>
				<button
					type="button"
					onclick={() => (reload += 1)}
					class="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
				>
					Retry
				</button>
			</div>
		{:else if status === 'not-found'}
			<div class="rounded-lg border border-kraft-300 bg-ink-0 p-10 text-center shadow-card">
				<h2 class="text-title text-ink-900">Listing not found</h2>
				<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
					This listing may have been removed, or the link is out of date.
				</p>
				<div class="mt-6">
					<a
						href="/search"
						class="inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
					>
						Browse listings
					</a>
				</div>
			</div>
		{:else if restaurant}
			{#if saved}
				<div class="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card">
					<div class="flex items-center justify-between gap-3">
						<h1 class="text-title text-ink-900">Changed saved</h1>
						{#if verified}<VerifiedBadge />{:else}<UnverifiedTag />{/if}
					</div>
					<p class="mt-3 text-body text-ink-500">
						<span class="font-medium text-ink-900">{saved.name}</span>'s details are up to date.
						{#if verified}
							Verification status is preserved on edit.
						{:else}
							The listing is still unverified.
						{/if}
					</p>
					<div class="mt-6 flex flex-wrap items-center gap-3">
						<a
							href="/restaurants/{encodeURIComponent(saved.id)}"
							class="inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
						>
							View listing
						</a>
					</div>
				</div>
			{:else}
				<div>
					<header class="mb-6">
						<div class="flex flex-wrap items-center gap-2">
							<h1 class="text-title text-ink-900">Edit listing</h1>
							{#if verified}<VerifiedBadge />{:else}<UnverifiedTag />{/if}
						</div>
						<p class="mt-2 text-body text-ink-500">
							Update the details for <span class="font-medium text-ink-700">{restaurant.name}</span
							>. Changes apply immediately.
						</p>
					</header>
					<ListingForm
						{onSubmit}
						submitLabel="Save changes"
						submittingLabel="Saving changes…"
						{serverError}
						defaultValues={toFormValues(restaurant)}
					/>
				</div>
			{/if}
		{/if}
	</main>
{/if}
