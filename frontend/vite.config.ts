import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';
import { svelteTesting } from '@testing-library/svelte/vite';

export default defineConfig({
	plugins: [
		tailwindcss(),
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			adapter: adapter()
		}),
		svelteTesting()
	],
	test: {
		environment: 'jsdom',
		globals: true,
		include: ['src/**/*.{test,spec}.{js,ts}'],
		setupFiles: ['./src/test/setup.ts'],
		// Memory guard: jsdom + a bounded fork pool instead of Vitest browser mode
		// (Playwright/Chromium is the single biggest memory consumer in this
		// toolchain and must stay out of the default container run).
		pool: 'forks',
		maxWorkers: 2,
		restoreMocks: true
	}
});
