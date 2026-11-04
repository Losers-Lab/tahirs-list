<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import SiteShell from '#lib/components/layout/SiteShell.svelte';
	import VerifiedBadge from '#lib/components/trust/VerifiedBadge.svelte';
	import UnverifiedTag from '#lib/components/trust/UnverifiedTag.svelte';
	import HandCutIndicator from '#lib/components/trust/HandCutIndicator.svelte';
	import DeliveryIndicator from '#lib/components/trust/DeliveryIndicator.svelte';
	import SealMark from '#lib/components/trust/SealMark.svelte';
	import CertificatePanel from '#lib/components/detail/CertificatePanel.svelte';
	import MapPreview from '#lib/components/detail/MapPreview.svelte';
	import RestaurantPhoto from '#lib/components/listing/RestaurantPhoto.svelte';
	import FavoriteButton from '#lib/components/listing/FavoriteButton.svelte';
	import type { Restaurant } from '#lib/listings/restaurants';
	import { expiryState, fullAddress, verificationStatus } from '#lib/listings/restaurants';
	import { getRestaurant } from '#lib/listings/data';

	let restaurant = $state<Restaurant | null>(null);
	let status = $state<'loading' | 'success' | 'error'>('loading');
	let reload = $state(0);
	let generation = 0;

	// Compute "today" only in the browser so SSR and hydration agree.
	let todayIdx = $state(-1);
	onMount(() => {
		todayIdx = (new Date().getDay() + 6) % 7; // Mon=0..Sun=6
	});

	async function load(id: string) {
		const gen = ++generation;
		status = 'loading';
		try {
			const result = await getRestaurant(id);
			if (gen !== generation) return;
			restaurant = result ?? null;
			status = 'success';
		} catch {
			if (gen === generation) status = 'error';
		}
	}

	$effect(() => {
		const id = page.params.id ?? '';
		void reload;
		void load(id);
	});

	const verified = $derived(restaurant ? verificationStatus(restaurant) === 'VERIFIED' : false);
	const certState = $derived(
		verified && restaurant ? expiryState(restaurant.certificate) : 'none'
	);
	// An expired cert downgrades the badge everywhere (detail-page.md §1.2).
	const showVerifiedBadge = $derived(verified && certState !== 'expired');

	const meta = $derived(
		restaurant
			? [
					restaurant.cuisine,
					restaurant.distanceMi != null ? `${restaurant.distanceMi.toFixed(1)} mi` : null,
					restaurant.rating != null
						? `★ ${restaurant.rating.toFixed(1)} (${restaurant.reviewCount ?? 0})`
						: null
				]
					.filter(Boolean)
					.join(' · ')
			: ''
	);

	function goBack() {
		if (typeof window !== 'undefined' && window.history.length > 1) history.back();
		else void goto('/search?q=');
	}
</script>

<SiteShell mainClass="mx-auto max-w-[1200px] px-5 py-8 pb-32 lg:pb-8">
	<div class="mb-6">
		<button
			type="button"
			onclick={goBack}
			class="text-small text-ink-700 underline-offset-2 hover:text-brand-600 hover:underline"
		>
			← Back to results
		</button>
	</div>

	{#if status === 'loading'}
		<div aria-busy="true" class="space-y-6">
			<div class="aspect-[16/7] rounded-lg bg-ink-100"></div>
			<div class="h-8 w-64 rounded bg-ink-100"></div>
			<div class="h-4 w-72 rounded bg-ink-100"></div>
			<div class="grid gap-6 lg:grid-cols-2">
				<div class="h-64 rounded-lg border-[1.5px] border-dashed border-stamp-200 bg-ink-0"></div>
				<div class="h-64 rounded-lg bg-ink-100"></div>
			</div>
		</div>
	{:else if status === 'error'}
		<div class="rounded-lg border border-kraft-300 bg-ink-0 p-10 text-center shadow-card">
			<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
			<h2 class="mt-4 text-title text-ink-900">We couldn't load this restaurant.</h2>
			<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
				Something went wrong on our end. Try again.
			</p>
			<div class="mt-6">
				<button
					type="button"
					onclick={() => (reload += 1)}
					class="inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
				>
					Retry
				</button>
			</div>
		</div>
	{:else if !restaurant}
		<div class="rounded-lg border border-kraft-300 bg-ink-0 p-10 text-center shadow-card">
			<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
			<h2 class="mt-4 text-title text-ink-900">We couldn't find that restaurant.</h2>
			<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
				It may have been removed or the link is out of date.
			</p>
			<div class="mt-6">
				<a
					href="/search"
					class="inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
				>
					Browse all spots
				</a>
			</div>
		</div>
	{:else}
		<div>
			<div class="relative aspect-[16/7] overflow-hidden rounded-lg border border-kraft-200 shadow-card">
				<RestaurantPhoto
					src={restaurant.imageUrl}
					alt={restaurant.name}
					sizes="(min-width: 1200px) 1200px, 100vw"
					eager
				/>
				{#if showVerifiedBadge}
					<div class="absolute right-4 top-4"><VerifiedBadge variant="on-photo" /></div>
				{/if}
			</div>

			<div class="mt-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
				<div>
					<div class="flex flex-wrap items-center gap-2">
						<h1 class="font-display text-[2rem] font-extrabold leading-[2.375rem] text-ink-900">
							{restaurant.name}
						</h1>
						{#if verified && showVerifiedBadge}
							<VerifiedBadge />
						{:else}
							<UnverifiedTag />
						{/if}
					</div>
					<p class="mt-1 text-body text-ink-500">{meta}</p>
					{#if restaurant.isHandCut === true}
						<div class="mt-3"><HandCutIndicator /></div>
					{/if}
					{#if restaurant.isDelivery === true}
						<div class="mt-3"><DeliveryIndicator /></div>
					{/if}
				</div>

				<div class="flex flex-col gap-2 sm:flex-row lg:flex-col lg:items-stretch xl:flex-row">
					<FavoriteButton listingId={restaurant.id} {restaurant} variant="detail" />
					<a
						href="https://www.google.com/maps/dir/?api=1&destination={restaurant.lat},{restaurant.lng}"
						target="_blank"
						rel="noreferrer"
						class="inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-5 text-label text-cream-50 shadow-chip hover:bg-ink-700"
					>
						Directions
					</a>
					{#if restaurant.phone}
						<a
							href="tel:{restaurant.phone}"
							class="inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-5 text-label text-cream-50 shadow-chip hover:bg-ink-700"
						>
							Call
						</a>
					{/if}
					{#if restaurant.website}
						<a
							href={restaurant.website}
							target="_blank"
							rel="noreferrer"
							class="inline-flex h-11 items-center justify-center rounded-md border-[1.5px] border-kraft-200 bg-ink-0 px-5 text-label text-ink-700 shadow-chip hover:bg-ink-100"
						>
							Website
						</a>
					{/if}
				</div>
			</div>

			<div class="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
				<div>
					{#if restaurant.certificate && verified}
						<CertificatePanel certificate={restaurant.certificate} name={restaurant.name} />
					{:else}
						<section
							aria-label="Verification status"
							class="rounded-lg border-[1.5px] border-ink-300 bg-ink-100 p-6"
						>
							<p class="text-body text-ink-700">
								This listing hasn't been verified yet. Owners can claim it and submit their
								certification for review.
							</p>
							<a
								href="/how-verification-works"
								class="mt-3 inline-block text-label text-brand-500 underline-offset-2 hover:underline"
							>
								How verification works
							</a>
						</section>
					{/if}
				</div>

				<div class="grid gap-6">
					{#if restaurant.hours}
						<section aria-label="Hours">
							<h2 class="text-heading text-ink-900">Hours</h2>
							<ul class="mt-2 divide-y divide-kraft-100 text-body">
								{#each restaurant.hours as hour, i (hour.day)}
									{@const isToday = i === todayIdx}
									<li
										class="flex items-center justify-between py-1.5 {isToday
											? 'font-semibold text-ink-900'
											: 'text-ink-700'}"
									>
										<span>{hour.day}</span>
										<span class="flex items-center gap-2">
											{hour.value}
											{#if isToday}
												<span
													class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-small font-medium text-stamp-700"
													style="background-color: var(--color-stamp-50);"
												>
													<span aria-hidden="true">●</span> Open
												</span>
											{/if}
										</span>
									</li>
								{/each}
							</ul>
						</section>
					{/if}

					<section aria-label="Location">
						<h2 class="text-heading text-ink-900">Location</h2>
						<p class="mt-2 text-body text-ink-700">{fullAddress(restaurant)}</p>
						<div class="mt-3">
							<MapPreview lat={restaurant.lat} lng={restaurant.lng} restaurantName={restaurant.name} />
						</div>
						<a
							class="mt-2 inline-block text-label text-brand-500 underline-offset-2 hover:underline"
							href="https://www.google.com/maps/search/?api=1&query={encodeURIComponent(fullAddress(restaurant))}"
							target="_blank"
							rel="noreferrer"
						>
							Get directions
						</a>
					</section>
				</div>
			</div>
		</div>
	{/if}
</SiteShell>
