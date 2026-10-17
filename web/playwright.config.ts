import { defineConfig, devices } from '@playwright/test';
const port = process.env.WEB_PORT ?? '5187';
const baseURL = process.env.WEB_BASE_URL ?? `http://127.0.0.1:${port}`;
export default defineConfig({
  testDir: './tests', fullyParallel: false, workers: 1,
  use: { baseURL, trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${port}`, url: baseURL, reuseExistingServer: false,
    env: {
      API_TARGET: process.env.API_TARGET ?? 'http://127.0.0.1:8080',
      VITE_ENABLE_OPERATIONAL_TEST_PANELS: process.env.VITE_ENABLE_OPERATIONAL_TEST_PANELS ?? 'false',
    },
  },
});
