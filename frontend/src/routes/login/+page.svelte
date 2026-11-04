<script lang="ts">
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import { tick } from 'svelte';
	import AuthCard from '#lib/components/auth/AuthCard.svelte';
	import Alert from '#lib/components/auth/Alert.svelte';
	import Button from '#lib/components/auth/Button.svelte';
	import Field from '#lib/components/auth/Field.svelte';
	import PasswordField from '#lib/components/auth/PasswordField.svelte';
	import { api, ApiError } from '#lib/api/client';
	import { signIn } from '#lib/auth/session.svelte';
	import { loginSchema } from '#lib/auth/schemas';

	const created = $derived(page.url.searchParams.get('created') === '1');

	let email = $state('');
	let password = $state('');
	let errors = $state<Record<string, string>>({});
	let serverError = $state<string | null>(null);
	let credentialError = $state<string | null>(null);
	let submitting = $state(false);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		serverError = null;
		credentialError = null;

		const result = loginSchema.safeParse({ email, password });
		if (!result.success) {
			const next: Record<string, string> = {};
			for (const issue of result.error.issues) {
				const key = String(issue.path[0] ?? '');
				if (key && !next[key]) next[key] = issue.message;
			}
			errors = next;
			return;
		}
		errors = {};
		submitting = true;
		try {
			const auth = await api.login(result.data);
			signIn(auth, result.data.email);
			await goto('/');
		} catch (error) {
			if (error instanceof ApiError && error.code === 'invalid_credentials') {
				// Single combined message — never reveal which field failed
				// (anti-enumeration). Keep the email, clear + refocus the password.
				credentialError = 'Incorrect email or password.';
				password = '';
				// Re-enable the form before focusing: a disabled input cannot take
				// focus, and the field is disabled while the request is in flight.
				submitting = false;
				await tick();
				document.querySelector<HTMLInputElement>('input[autocomplete="current-password"]')?.focus();
				return;
			}
			serverError = 'Something went wrong. Please try again.';
		} finally {
			submitting = false;
		}
	}
</script>

{#snippet footer()}
	New here?&#32;
	<a href="/signup" class="font-medium text-brand-500 hover:text-brand-600 hover:underline">
		Sign up
	</a>
{/snippet}

{#snippet forgotPassword()}
	<a
		href="#"
		class="text-small text-brand-500 hover:text-brand-600 hover:underline"
		onclick={(event) => event.preventDefault()}
	>
		Forgot password?
	</a>
{/snippet}

<AuthCard heading="Welcome back" subcopy="Log in to continue finding verified halal food." {footer}>
	{#if created}
		<div
			role="status"
			class="mb-4 rounded-md border border-ink-300 bg-ink-100 px-3 py-2.5 text-small text-ink-700"
		>
			Account created. Log in to continue.
		</div>
	{/if}

	{#if serverError}
		<Alert variant="banner">{serverError}</Alert>
	{/if}

	{#if credentialError}
		<div class="mb-4">
			<Alert variant="inline">{credentialError}</Alert>
		</div>
	{/if}

	<form onsubmit={submit} novalidate class="space-y-4">
		<Field
			label="Email"
			error={errors.email}
			bind:value={email}
			type="email"
			autocomplete="email"
			placeholder="you@example.com"
			disabled={submitting}
		/>

		<PasswordField
			label="Password"
			error={errors.password}
			labelEnd={forgotPassword}
			bind:value={password}
			autocomplete="current-password"
			disabled={submitting}
		/>

		<div class="pt-2">
			<Button type="submit" loading={submitting} loadingLabel="Logging in…" class="w-full">
				Log in
			</Button>
		</div>
	</form>
</AuthCard>
