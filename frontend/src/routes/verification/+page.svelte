<script lang="ts">
	import SiteShell from '#lib/components/layout/SiteShell.svelte';
	import ReviewCard from '#lib/components/reviews/ReviewCard.svelte';
	import SealMark from '#lib/components/trust/SealMark.svelte';
	import { sessionState } from '#lib/auth/session.svelte';
	import { VerificationQueueController } from '#lib/reviews/queue.svelte';

	const queue = new VerificationQueueController();
	const session = $derived(sessionState.current);
	const restoring = $derived(sessionState.restoring);
	const isCommittee = $derived(session?.role === 'VERIFICATION_COMMITTEE');

	$effect(() => {
		void queue.load(session?.accountId ?? null, session?.role ?? null);
	});
</script>

<SiteShell>
	<header>
		<h1 class="text-title text-ink-900">Verification Committee</h1>
		<p class="mt-2 max-w-2xl text-body text-ink-500">
			Review and decide each pending certification. You are confirming that the restaurant may be
			shown as verified.
		</p>
	</header>

	{#if restoring && !session}
		<div aria-busy="true" class="mt-8 h-40 animate-pulse rounded-lg bg-ink-100"></div>
	{/if}

	{#if !restoring && !session}
		<div
			class="mt-8 rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 p-10 text-center shadow-card"
		>
			<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Signed out" />
			<h2 class="mt-4 text-title text-ink-900">Verification Committee sign in</h2>
			<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
				Only a Verification Committee member can work the review queue.
			</p>
			<a
				href="/login"
				class="mt-6 inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-label text-cream-50 shadow-chip hover:bg-brand-600"
			>
				Log in
			</a>
		</div>
	{/if}

	{#if session && !isCommittee}
		<div class="mt-8 rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-10 text-center">
			<h2 class="text-title text-ink-900">No access to this queue</h2>
			<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
				This screen is for the Verification Committee only. If you think this is a mistake, contact
				an administrator.
			</p>
		</div>
	{/if}

	{#if isCommittee}
		<div class="mt-8">
			{#if queue.notice}
				<div
					role="status"
					class="mb-5 flex items-center justify-between gap-3 rounded-md border border-stamp-200 bg-stamp-50 px-4 py-3 text-body text-stamp-700"
				>
					<span>{queue.notice}</span>
					<button
						type="button"
						onclick={() => queue.clearNotice()}
						aria-label="Dismiss"
						class="rounded-md px-1.5 text-stamp-700 hover:bg-stamp-100"
					>
						×
					</button>
				</div>
			{/if}

			{#if queue.status === 'loading'}
				<div aria-busy="true" class="space-y-4">
					<div class="h-40 animate-pulse rounded-lg bg-ink-100"></div>
					<div class="h-40 animate-pulse rounded-lg bg-ink-100"></div>
				</div>
			{/if}

			{#if queue.status === 'error'}
				<div class="rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-8 text-center">
					<h2 class="text-title text-ink-900">We couldn't load the review queue.</h2>
					<button
						type="button"
						onclick={() => void queue.load(session?.accountId ?? null, session?.role ?? null)}
						class="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
					>
						Retry
					</button>
				</div>
			{/if}

			{#if queue.status === 'ready' && queue.reviews.length === 0}
				<div class="rounded-lg border border-dashed border-kraft-300 bg-ink-0 p-10 text-center">
					<SealMark class="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
					<h2 class="mt-4 text-title text-ink-900">No pending verifications</h2>
					<p class="mx-auto mt-2 max-w-md text-body text-ink-500">
						The queue is clear. New certification reviews will appear here once an owner submits
						proof and the AI suggests a verdict.
					</p>
				</div>
			{/if}

			{#if queue.status === 'ready' && queue.reviews.length > 0}
				<div class="space-y-5">
					{#each queue.reviews as review (review.reviewId)}
						<ReviewCard
							{review}
							isDeciding={queue.isDeciding(review.reviewId)}
							onApprove={(reason) => queue.approve(review.reviewId, reason)}
							onDeny={(reason) => queue.deny(review.reviewId, reason)}
							error={queue.decisionError?.reviewId === review.reviewId
								? queue.decisionError.message
								: undefined}
						/>
					{/each}
				</div>
			{/if}
		</div>
	{/if}
</SiteShell>
