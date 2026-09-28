import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { Role } from '@/features/auth/types'
import { fakeSheet } from '@/test/arrivalFixtures'
import { me, mockFetch, problem, renderApp, setBrand, type MockResponse } from '@/test/utils'
import { formatAge } from './labels'

function renderSheet(response: MockResponse, role: Role = 'HOST') {
  mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me(role) }
    if (url === '/api/visits/v-1/sheet') return response
  })
  return renderApp('/chegadas/v-1/ficha')
}

const dd = (term: string) => screen.getByText(term, { selector: 'dt' }).nextElementSibling?.textContent

const items = (title: string) =>
  within(screen.getByRole('region', { name: title }))
    .queryAllByRole('listitem')
    .map((item) => item.textContent)

afterEach(() => {
  vi.restoreAllMocks()
})

describe('F6 — montagem da ficha', () => {
  it('mostra todos os campos da §16.6', async () => {
    renderSheet({ body: fakeSheet() })

    expect(await screen.findByRole('heading', { name: 'Ficha da visita — 24/09/2026' })).toBeInTheDocument()
    expect(dd('Lead')).toBe('Lead Fictício')
    expect(dd('Idade')).toBe('34 anos')
    expect(dd('Telefone')).toBe('11 90000-0000')
    expect(dd('Prospector')).toBe('Prospector Fictício')
    expect(dd('Horário de entrada')).toBe('10:05')
    expect(items('Acompanhantes presentes')).toEqual(['Acompanhante UmCônjuge · 33 anos', 'Acompanhante DoisFilho(a) · 1 ano'])
    // Ausentes sem idade.
    expect(items('Acompanhantes ausentes')).toEqual(['Acompanhante TrêsAmigo(a)'])
    expect(within(screen.getByRole('region', { name: 'Observações para o anfitrião' })).getByText(
      'Prefere conhecer as piscinas primeiro.',
    )).toBeInTheDocument()
  })

  it('sem data de nascimento, telefone, acompanhantes e observações', async () => {
    renderSheet({
      body: fakeSheet({ lead: { name: 'Lead Fictício', age: null, phone: null }, presentCompanions: [], absentCompanions: [], hostNotes: null }),
    })

    await screen.findByRole('heading', { name: /Ficha da visita/ })
    expect(dd('Idade')).toBe('—')
    expect(dd('Telefone')).toBe('—')
    for (const title of ['Acompanhantes presentes', 'Acompanhantes ausentes']) {
      expect(within(screen.getByRole('region', { name: title })).getByText('Nenhum')).toBeInTheDocument()
    }
    expect(screen.getByText('Sem observações')).toBeInTheDocument()
  })

  it('formata a idade', () => {
    expect(formatAge(34)).toBe('34 anos')
    expect(formatAge(1)).toBe('1 ano')
    expect(formatAge(0)).toBe('menos de 1 ano')
    expect(formatAge(null)).toBe('—')
  })
})

describe('F7 — ficha indisponível', () => {
  it('404 mostra "Visita não encontrada."', async () => {
    renderSheet(problem(404, 'VISIT_NOT_FOUND'))

    expect(await screen.findByText('Visita não encontrada.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Voltar às chegadas' })).toHaveAttribute('href', '/chegadas')
  })

  it('409 VISIT_NOT_ARRIVED explica que ainda não houve entrada', async () => {
    renderSheet(problem(409, 'VISIT_NOT_ARRIVED'), 'ADMIN')

    expect(
      await screen.findByText(
        'Esta visita ainda não teve entrada registrada. A ficha fica disponível depois da entrada na Portaria.',
      ),
    ).toBeInTheDocument()
  })
})

describe('F8 — impressão', () => {
  it('[Imprimir] chama window.print; cabeçalho, menu e botões ficam fora da impressão', async () => {
    const print = vi.fn()
    vi.stubGlobal('print', print)
    const user = userEvent.setup()
    renderSheet({ body: fakeSheet() })

    await user.click(await screen.findByRole('button', { name: 'Imprimir' }))

    expect(print).toHaveBeenCalledTimes(1)
    const header = screen.getByRole('banner')
    expect(header).toHaveClass('print:hidden')
    expect(within(header).getByRole('navigation', { name: 'Menu principal' })).toBeInTheDocument()
    expect(screen.getByTestId('sheet-actions')).toHaveClass('print:hidden')
    expect(within(screen.getByTestId('sheet-actions')).getAllByRole('button').length
      + within(screen.getByTestId('sheet-actions')).getAllByRole('link').length).toBe(2)
    // Nada da ficha está escondido na impressão.
    expect(screen.getByRole('article').closest('.print\\:hidden')).toBeNull()
  })
})

describe('F9 — identidade na ficha (D-117)', () => {
  const RESORT = 'Resort Fictício das Águas'

  async function identity() {
    renderSheet({ body: fakeSheet() })
    await screen.findByRole('heading', { name: 'Ficha da visita — 24/09/2026' })
    return screen.getByTestId('sheet-identity')
  }

  it('com nome e logotipo: o logotipo por <img>, limitado a 12 mm × 45 mm na impressão', async () => {
    setBrand({ name: RESORT, logo: '/brand/logo.png' })
    const image = within(await identity()).getByRole('img')
    expect(image).toHaveAttribute('src', '/brand/logo.png')
    expect(image).toHaveAccessibleName(RESORT)
    expect(image).toHaveClass('print:h-[12mm]', 'print:max-w-[45mm]', 'object-contain')
  })

  it('só com RESORT_NAME: o nome em texto, nunca "Resortric"', async () => {
    setBrand({ name: RESORT })
    const box = await identity()
    expect(box).toHaveTextContent(RESORT)
    expect(within(box).queryByRole('img')).not.toBeInTheDocument()
  })

  it('sem tema: o logotipo do Resortric', async () => {
    const box = await identity()
    expect(within(box).getByRole('img', { name: 'Resortric' })).toHaveAttribute('src', '/resortric.svg')
  })
})

