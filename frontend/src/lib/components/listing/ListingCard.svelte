<script lang="ts">
	import RestaurantPhoto from './RestaurantPhoto.svelte';
	import PhotoCredit from './PhotoCredit.svelte';
	import FavoriteButton from './FavoriteButton.svelte';
	import VerifiedBadge from '#lib/components/trust/VerifiedBadge.svelte';
	import UnverifiedTag from '#lib/components/trust/UnverifiedTag.svelte';
	import HandCutIndicator from '#lib/components/trust/HandCutIndicator.svelte';
	import DeliveryIndicator from '#lib/components/trust/DeliveryIndicator.svelte';
	import type { Restaurant } from '#lib/listings/restaurants';
	import {
		cardThumbFallback,
		cardThumbSource,
		verificationStatus
	} from '#lib/listings/restaurants';

	type Props = { restaurant: Restaurant };
	let { restaurant }: Props = $props();

	const verified = $derived(verificationStatus(restaurant) === 'VERIFIED');
	const srcset = $derived(
		restaurant.imageSrcset?.length
			? restaurant.imageSrcset.map((entry) => `${entry.url} ${entry.width}w`).join(', ')
			: undefined
	);

	const meta = $derived(
		[
			restaurant.cuisine,
			restaurant.distanceMi != null ? `${restaurant.distanceMi.toFixed(1)} mi` : null,
			restaurant.rating != null
				? `★ ${restaurant.rating.toFixed(1)}${restaurant.reviewCount != null ? ` (${restaurant.reviewCount})` : ''}`
				: null
		]
			.filter(Boolean)
			.join(' · ')
	);
</script>

<article
	class="group relative overflow-hidden rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 shadow-card transition-transform duration-200 ease-out hover:-translate-y-0.5 hover:shadow-pop motion-reduce:transition-none motion-reduce:hover:translate-y-0"
>
	<div class="relative aspect-video bg-kraft-100">
		<!-- sizes="100vw" is the founder's max-monitor baseline (sc-183) — do not narrow it. -->
		<RestaurantPhoto
			src={cardThumbSource(restaurant)}
			fallbackSrc={cardThumbFallback(restaurant)}
			{srcset}
			alt={restaurant.name}
			sizes="100vw"
		/>
		<div class="absolute top-3 left-3 z-10">
			<FavoriteButton listingId={restaurant.id} {restaurant} variant="card" />
		</div>
		{#if verified}
			<div class="absolute top-3 right-3">
				<VerifiedBadge variant="on-photo" />
			</div>
		{/if}
		<PhotoCredit
			attribution={restaurant.photoAttribution}
			licence={restaurant.photoLicence}
			sourceUrl={restaurant.photoSourceUrl}
			variant="card"
		/>
	</div>

	<div class="p-5">
		<div class="flex items-start justify-between gap-2">
			<h3 class="text-heading text-ink-900">
				<a
					href="/restaurants/{restaurant.id}"
					class="after:absolute after:inset-0 after:content-[''] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
				>
					{restaurant.name}
				</a>
			</h3>
			<span class="shrink-0">
				{#if verified}<VerifiedBadge />{:else}<UnverifiedTag />{/if}
			</span>
		</div>

		{#if meta}<p class="mt-1 text-small text-ink-500">{meta}</p>{/if}

		{#if restaurant.isHandCut === true}
			<div class="mt-3"><HandCutIndicator /></div>
		{/if}

		{#if restaurant.isDelivery === true}
			<div class="mt-3"><DeliveryIndicator /></div>
		{/if}
	</div>
</article>
