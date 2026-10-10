import { defineConfig, devices } from '@playwright/test';
export default defineConfig({
  testDir: './tests', fullyParallel: false, workers: 1,
  use: { baseURL: process.env.WEB_BASE_URL ?? 'http://127.0.0.1:5187', trace: 'retain-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev -- --port 5187', url: 'http://127.0.0.1:5187', reuseExistingServer: false,
    env: {
      API_TARGET: process.env.API_TARGET ?? 'http://127.0.0.1:8080',
      VITE_ENABLE_OPERATIONAL_TEST_PANELS: process.env.VITE_ENABLE_OPERATIONAL_TEST_PANELS ?? 'false',
    },
  },
});
