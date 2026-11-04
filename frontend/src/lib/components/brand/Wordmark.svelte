<script lang="ts">
	type Props = { class?: string };
	let { class: className = '' }: Props = $props();
</script>

<!-- Wordmark "Tahir's List" — the apostrophe is always brand red. -->
<span class="font-wordmark font-black tracking-[-0.03em] text-ink-900 {className}">
	Tahir<span class="text-brand-500">&#39;</span>s List
</span>
