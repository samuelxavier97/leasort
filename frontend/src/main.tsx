import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { App } from './app/App'
import { applyBrand, currentBrand } from './lib/brand'

// Cor do cliente antes do primeiro render, para não piscar a cor padrão (D-115).
applyBrand(document.documentElement, currentBrand())

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
