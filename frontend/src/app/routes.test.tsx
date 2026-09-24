import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { routes } from './routes'

describe('routes', () => {
  it('renderiza a página inicial provisória', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/'] })

    render(<RouterProvider router={router} />)

    expect(screen.getByRole('heading', { name: 'Gestão de Visitas do Resort' })).toBeInTheDocument()
  })
})
