import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// URLs de Blob do jsdom falham com os Blobs das respostas; a tela do convite as usa para o QR.
URL.createObjectURL = () => 'blob:teste'
URL.revokeObjectURL = () => undefined

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})
