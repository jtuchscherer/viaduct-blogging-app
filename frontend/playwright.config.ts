import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  // Tests share a live backend — run sequentially to avoid data conflicts
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  timeout: 60000, // 60 s per test — the full suite takes ~2 min so the dev server can
                  // slow down by the time webkit runs; 30 s is too tight late in the run.
  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: process.env.FRONTEND_URL ?? 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      // webkit accumulates browser-process state across contexts in a long sequential run
      // (~250 contexts) which causes a single page.goto to stall for a full TCP timeout
      // (60 s).  A 10-second navigation timeout surfaces the failure quickly so the
      // built-in retry can spin up a fresh context without burning the full test budget.
      name: 'webkit',
      use: { ...devices['Desktop Safari'], navigationTimeout: 10_000 },
      retries: process.env.CI ? 2 : 1,
    },
  ],
});
