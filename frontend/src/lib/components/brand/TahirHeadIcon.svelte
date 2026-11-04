<script lang="ts">
	import tahirHead from '$lib/assets/tahir-head.jpg';

	type Props = { class?: string };
	let { class: className = '' }: Props = $props();
</script>

<!-- C2 head app-icon — decorative within a labeled link, so alt is empty. -->
<img
	src={tahirHead}
	alt=""
	width="112"
	height="112"
	class="rounded-[29%] object-cover shadow-pop {className}"
/>
