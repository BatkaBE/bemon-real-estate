import { defineConfig, devices } from '@playwright/test';
import { existsSync } from 'node:fs';
if (existsSync('../.local.env')) process.loadEnvFile('../.local.env');
/** Exercises the actual local stack; API mocks and stored token fixtures are intentionally unnecessary. */
export default defineConfig({
  testDir: './tests/e2e', timeout: 60000, expect: { timeout: 10000 }, workers: 1,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: process.env.APP_ORIGIN || 'http://localhost:3000', screenshot: 'only-on-failure',
    // OAuth traces can contain passwords and tokens; omit them from shared test artifacts.
    trace: 'off' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
