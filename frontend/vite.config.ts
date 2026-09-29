import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': path.resolve(import.meta.dirname, './src'),
    },
  },
  server: {
    // Mesma origem em desenvolvimento: o Vite repassa /api ao backend (D-029).
    proxy: {
      '/api': 'http://localhost:8080',
    },
    // Só no Vitest: os PNGs do teste com câmera (D-123) ficam fora de frontend/.
    ...(process.env.VITEST ? { fs: { allow: ['.', '../docs/qr-teste-camera'] } } : {}),
  },
  test: {
    environment: 'jsdom',
    // Só os testes de unidade; o E2E (e2e/*.spec.ts) roda no Playwright.
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test/setup.ts'],
  },
})
