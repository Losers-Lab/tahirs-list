<script lang="ts">
	import { goto } from '$app/navigation';
	import AuthCard from '#lib/components/auth/AuthCard.svelte';
	import Alert from '#lib/components/auth/Alert.svelte';
	import Button from '#lib/components/auth/Button.svelte';
	import Field from '#lib/components/auth/Field.svelte';
	import PasswordField from '#lib/components/auth/PasswordField.svelte';
	import { api, ApiError } from '#lib/api/client';
	import { signupSchema } from '#lib/auth/schemas';

	let email = $state('');
	let password = $state('');
	let errors = $state<Record<string, string>>({});
	let serverError = $state<string | null>(null);
	// Server-reported duplicate email. Tracked separately so "logging in" can be
	// rendered as an inline link inside the field error; cleared on edit.
	let emailConflict = $state(false);
	let submitting = $state(false);

	async function submit(event: SubmitEvent) {
		event.preventDefault();
		serverError = null;
		emailConflict = false;

		const result = signupSchema.safeParse({ email, password });
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
			await api.signup(result.data);
			// The signup response carries no session, so route to Log In with a
			// success notice rather than auto-logging in.
			await goto('/login?created=1');
		} catch (error) {
			if (error instanceof ApiError) {
				if (error.code === 'email_already_exists') {
					emailConflict = true;
					return;
				}
				if (error.code === 'weak_password') {
					const detail = error.detail?.trim();
					errors = {
						password: detail ? detail : 'Password is too weak. Use at least 8 characters.'
					};
					return;
				}
			}
			serverError = 'Something went wrong. Please try again.';
		} finally {
			submitting = false;
		}
	}
</script>

{#snippet footer()}
	Already have an account?{' '}
	<a href="/login" class="font-medium text-brand-500 hover:text-brand-600 hover:underline">
		Log in
	</a>
{/snippet}

{#snippet emailConflictError()}
	An account with this email already exists. Try{' '}
	<a
		href="/login"
		class="font-medium text-brand-600 underline underline-offset-2 hover:text-brand-700"
	>
		logging in
	</a>{' '}
	instead.
{/snippet}

<AuthCard
	heading="Create your account"
	subcopy="Find verified halal food near you."
	trustLine
	footer={footer}
>
	{#if serverError}
		<Alert variant="banner">{serverError}</Alert>
	{/if}

	<form onsubmit={submit} novalidate class="space-y-4">
		<Field
			label="Email"
			error={emailConflict ? emailConflictError : errors.email}
			bind:value={email}
			oninput={() => {
				if (emailConflict) emailConflict = false;
			}}
			type="email"
			autocomplete="email"
			placeholder="you@example.com"
			disabled={submitting}
		/>

		<PasswordField
			label="Password"
			error={errors.password}
			helper="At least 8 characters."
			bind:value={password}
			autocomplete="new-password"
			disabled={submitting}
		/>

		<div class="pt-2">
			<Button type="submit" loading={submitting} loadingLabel="Creating account…" class="w-full">
				Create account
			</Button>
		</div>
	</form>
</AuthCard>
