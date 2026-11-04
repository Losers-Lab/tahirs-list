<script lang="ts">
	type Props = {
		/** default = quiet dashed stamp badge; on-photo = solid stamp over imagery. */
		variant?: 'default' | 'on-photo';
	};
	let { variant = 'default' }: Props = $props();
</script>

{#if variant === 'on-photo'}
	<span
		class="inline-flex items-center gap-1 rounded-sm bg-stamp-500 px-2.5 py-1 text-small font-semibold text-cream-50 shadow-stamp"
		title="Verified — certification reviewed by our committee"
	>
		<svg
			aria-hidden="true"
			width="16"
			height="16"
			viewBox="0 0 16 16"
			fill="none"
			stroke="currentColor"
			stroke-width="2"
			stroke-linecap="round"
			stroke-linejoin="round"
		>
			<path d="M3 8.5 6.5 12 13 4.5" />
		</svg>
		Verified
	</span>
{:else}
	<span
		class="inline-flex items-center gap-1 rounded-sm border-[1.5px] border-dashed border-stamp-200 bg-stamp-50 px-2.5 py-1 text-small font-semibold text-stamp-700 shadow-stamp"
		title="Verified — certification reviewed by our committee"
	>
		<svg
			aria-hidden="true"
			width="16"
			height="16"
			viewBox="0 0 16 16"
			fill="none"
			stroke="currentColor"
			stroke-width="2"
			stroke-linecap="round"
			stroke-linejoin="round"
		>
			<path d="M3 8.5 6.5 12 13 4.5" />
		</svg>
		Verified
	</span>
{/if}
