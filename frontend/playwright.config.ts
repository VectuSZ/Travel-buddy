import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests',
  workers: 1,
  retries: 0,
  timeout: 45_000,
  use: { baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000', browserName: 'chromium', trace: 'off' },
})
