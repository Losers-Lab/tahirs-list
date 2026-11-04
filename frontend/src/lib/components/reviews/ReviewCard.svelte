<script lang="ts">
	import Button from '#lib/components/auth/Button.svelte';
	import type { ReviewWithListing } from '#lib/reviews/data';

	type Props = {
		review: ReviewWithListing;
		isDeciding: boolean;
		onApprove: (reason?: string) => void;
		onDeny: (reason: string) => void;
		error?: string;
	};

	let { review, isDeciding, onApprove, onDeny, error }: Props = $props();

	let denying = $state(false);
	let reason = $state('');
	let validation = $state<string | null>(null);

	const listing = $derived(review.listing);

	type SuggestionTone = 'approve' | 'deny' | 'review';
	const tone = $derived<SuggestionTone>(
		review.suggestedVerdict === 'APPROVE'
			? 'approve'
			: review.suggestedVerdict === 'DENY'
				? 'deny'
				: 'review'
	);
	const toneClasses = $derived(
		tone === 'approve'
			? 'border-stamp-200 bg-stamp-50 text-stamp-700'
			: tone === 'deny'
				? 'border-warning-100 bg-warning-50 text-warning-700'
				: 'border-kraft-200 bg-ink-100 text-ink-700'
	);
	const verdictLabel = $derived(
		`AI suggests: ${
			review.suggestedVerdict === 'APPROVE'
				? 'Approve'
				: review.suggestedVerdict === 'DENY'
					? 'Deny'
					: 'Needs review'
		}`
	);
	const confidence = $derived(
		review.suggestionConfidence != null
			? `${Math.round(review.suggestionConfidence * 100)}%`
			: null
	);

	function openDeny() {
		denying = true;
		validation = null;
		reason = '';
	}

	function cancelDeny() {
		denying = false;
		validation = null;
		reason = '';
	}

	function submitDeny() {
		const trimmed = reason.trim();
		if (!trimmed) {
			validation = 'Enter a reason to deny this verification.';
			return;
		}
		onDeny(trimmed);
	}
</script>

<article class="rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 p-5 shadow-card">
	<div class="flex flex-wrap items-start justify-between gap-3">
		<div class="min-w-0">
			{#if listing}
				<h3 class="text-heading text-ink-900">
					<a
						href="/restaurants/{listing.id}"
						class="hover:text-brand-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
					>
						{listing.name}
					</a>
				</h3>
				<p class="mt-1 text-small text-ink-500">
					{[listing.cuisine, listing.isHandCut === true ? 'Hand-cut' : null]
						.filter(Boolean)
						.join(' · ')}
				</p>
				<p class="mt-0.5 text-small text-ink-400">{listing.address}</p>
			{:else}
				<h3 class="text-heading text-ink-900">Listing unavailable</h3>
				<p class="mt-1 text-small text-ink-500">
					This review's listing no longer exists. Deny it to remove it from the queue.
				</p>
			{/if}
		</div>

		<span class="shrink-0 rounded-md border px-2.5 py-1 text-small font-medium {toneClasses}">
			{verdictLabel}{confidence ? ` · ${confidence}` : ''}
		</span>
	</div>

	{#if review.suggestionReasoning}
		<p class="mt-3 border-l-2 border-kraft-200 pl-3 text-small italic text-ink-500">
			{review.suggestionReasoning}
		</p>
	{/if}

	{#if error}
		<p role="alert" class="mt-4 text-small text-danger-600">{error}</p>
	{/if}

	<div class="mt-5 flex flex-wrap items-center gap-3">
		{#if !denying}
			<Button
				type="button"
				disabled={isDeciding}
				loading={isDeciding}
				loadingLabel="Approving…"
				onclick={() => onApprove()}
			>
				Approve verification
			</Button>
			<Button type="button" variant="ghost" disabled={isDeciding} onclick={openDeny}>
				Deny
			</Button>
		{:else}
			<label for="deny-reason-{review.reviewId}" class="sr-only">
				Reason for denying this verification
			</label>
			<input
				id="deny-reason-{review.reviewId}"
				type="text"
				bind:value={reason}
				oninput={() => {
					if (validation) validation = null;
				}}
				aria-invalid={validation ? true : undefined}
				aria-describedby={validation ? `deny-reason-error-${review.reviewId}` : undefined}
				placeholder="Why are you denying this?"
				disabled={isDeciding}
				class="h-11 min-w-56 flex-1 rounded-md border bg-ink-0 px-3 text-body text-ink-900 placeholder:text-ink-400 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 {validation
					? 'border-danger-500'
					: 'border-kraft-300'}"
			/>
			{#if validation}
				<span
					id="deny-reason-error-{review.reviewId}"
					role="alert"
					class="text-small text-danger-600"
				>
					{validation}
				</span>
			{/if}
			<Button
				type="button"
				disabled={isDeciding}
				loading={isDeciding}
				loadingLabel="Denying…"
				onclick={submitDeny}
			>
				Confirm deny
			</Button>
			<Button type="button" variant="ghost" disabled={isDeciding} onclick={cancelDeny}>
				Cancel
			</Button>
		{/if}
	</div>
</article>
