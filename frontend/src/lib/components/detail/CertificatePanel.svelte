<script lang="ts">
	import SealMark from '#lib/components/trust/SealMark.svelte';
	import { expiryState, formatDate, type Certificate } from '#lib/listings/restaurants';

	type Props = { certificate: Certificate; name: string };
	let { certificate, name }: Props = $props();

	const state = $derived(expiryState(certificate));
	const expired = $derived(state === 'expired');
</script>

<section
	aria-label="Halal verification"
	class="rounded-lg border-[1.5px] border-dashed border-stamp-200 bg-ink-0 p-6 shadow-stamp"
>
	<div class="flex items-center gap-2">
		<SealMark class="h-6 w-6 text-stamp-500" srLabel="Verified seal" />
		<h2 class="text-heading text-ink-900">Halal verification</h2>
	</div>

	<dl class="mt-4 grid gap-3 text-body">
		<div class="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
			<dt class="w-36 shrink-0 font-medium text-ink-500">Certifier</dt>
			<dd class="font-medium text-ink-700">{certificate.certifier}</dd>
		</div>
		<div class="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
			<dt class="w-36 shrink-0 font-medium text-ink-500">Last reviewed</dt>
			<dd class="text-ink-700">{formatDate(certificate.reviewedOn)}</dd>
		</div>
		<div class="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
			<dt class="w-36 shrink-0 font-medium text-ink-500">Expires</dt>
			<dd class="text-ink-700">{expired ? '—' : formatDate(certificate.expiresOn)}</dd>
		</div>
	</dl>

	<div class="mt-4">
		<a
			href={certificate.certificateUrl ?? '#'}
			class="text-label text-brand-500 underline-offset-2 hover:underline"
			aria-label="View halal certificate for {name}"
			target={certificate.certificateUrl ? '_blank' : undefined}
			rel="noreferrer"
		>
			View certificate →
		</a>
	</div>

	{#if state === 'expiring'}
		<p
			class="mt-4 flex items-start gap-2 rounded-md bg-warning-50 px-3 py-2.5 text-small text-warning-700"
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
				class="mt-0.5 h-4 w-4 shrink-0"
			>
				<path d="M8 2.5 14.5 13h-13L8 2.5Z" />
				<path d="M8 6.5v3M8 11.5h.01" />
			</svg>
			<span>Expires {formatDate(certificate.expiresOn)} — review in progress</span>
		</p>
	{:else if expired}
		<p
			class="mt-4 flex items-start gap-2 rounded-md bg-ink-100 px-3 py-2.5 text-small text-ink-700"
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
				class="mt-0.5 h-4 w-4 shrink-0"
			>
				<circle cx="8" cy="8" r="6.5" />
				<path d="M8 7v3.5M8 4.75h.01" />
			</svg>
			<span>
				Certification lapsed on {formatDate(certificate.expiresOn)}. We're following up with the
				restaurant.
			</span>
		</p>
	{/if}

	<p class="mt-4 text-small text-ink-500">
		Reviewed by our verification committee — not self-reported.&#32;
		<a href="/how-verification-works" class="text-brand-500 underline-offset-2 hover:underline">
			Learn more
		</a>
	</p>
</section>
