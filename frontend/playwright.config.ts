import { defineConfig, devices } from '@playwright/test'

// E2E (Fase 10, D-104): rode por scripts/e2e.sh, que sobe banco limpo, backend e frontend.
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  // Sem novas tentativas: uma falha intermitente precisa aparecer, não ser escondida.
  retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 90_000,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // Chromium já instalado na máquina (opcional); no CI, o do `playwright install`.
        launchOptions: process.env.E2E_CHROMIUM_PATH ? { executablePath: process.env.E2E_CHROMIUM_PATH } : {},
      },
    },
  ],
})
