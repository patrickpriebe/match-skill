import { defineConfig } from '@playwright/test'
import { execSync } from 'node:child_process'

const PORT = process.env.PLAYWRIGHT_PORT ?? '4176'
const BASE_URL = `http://127.0.0.1:${PORT}`
const BACKEND_TARGET = process.env.VITE_API_PROXY_TARGET ?? 'http://127.0.0.1:18089'

// Clean disposable validation rate-limit keys in Redis before running browser test runs
try {
  execSync('docker exec matchskill-validation-redis-1 redis-cli del ratelimit:register:127.0.0.1 ratelimit:login:127.0.0.1', { stdio: 'ignore' })
} catch {
  // Ignored when docker container is not named matchskill-validation-redis-1
}

export default defineConfig({
  testDir: './tests/live',
  timeout: 90000,
  workers: 1,
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results/live-browser-results.json' }],
  ],
  use: {
    baseURL: BASE_URL,
    channel: 'msedge',
    headless: true,
    screenshot: 'only-on-failure',
  },
  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: false,
    timeout: 60000,
    env: {
      VITE_API_PROXY_TARGET: BACKEND_TARGET,
    },
  },
})
