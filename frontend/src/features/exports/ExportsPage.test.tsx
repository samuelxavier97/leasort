import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { page } from '@/test/leadFixtures'
import { me, mockFetch, problem, renderApp, type MockResponse } from '@/test/utils'

const prospectors = page([
  { id: 'p-1', userId: 'u-1', name: 'Ana Prospectora', email: 'ana@resort.local', active: true, employeeCode: 'P-01', phone: null },
])

const csv = (name: string): MockResponse => ({
  raw: { bytes: new TextEncoder().encode('﻿ID;Nome\r\n'), type: 'text/csv;charset=UTF-8' },
  headers: { 'Content-Disposition': `attachment; filename="${name}"` },
})

function renderExports(respond: (url: string) => MockResponse = () => csv('leads-2026-09-24.csv')) {
  const urls: string[] = []
  const fetchMock = mockFetch((_method, url) => {
    if (url === '/api/auth/me') return { body: me('ADMIN') }
    if (url.startsWith('/api/prospectors')) return { body: prospectors }
    if (url.startsWith('/api/exports/')) {
      urls.push(url)
      return respond(url)
    }
  })
  return { urls, fetchMock, ...renderApp('/exportacoes') }
}

const block = (title: string) => screen.getByRole('region', { name: title })

afterEach(() => {
  vi.restoreAllMocks()
})

describe('XF2 — blocos de exportação', () => {
  it('sem filtros, pede o arquivo sem parâmetros e salva com o nome do Content-Disposition', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const saved: string[] = []
    click.mockImplementation(function (this: HTMLAnchorElement) {
      saved.push(this.download)
    })
    const user = userEvent.setup()
    const { urls } = renderExports(() => csv('leads-2026-09-24.csv'))

    await user.click(within(await screen.findByRole('region', { name: 'Leads' })).getByRole('button', { name: 'Baixar CSV' }))

    await waitFor(() => expect(saved).toEqual(['leads-2026-09-24.csv']))
    expect(urls).toEqual(['/api/exports/leads'])
  })

  it.each([
    { title: 'Leads', file: 'leads', status: 'Status do Lead', option: 'Descartado', value: 'CANCELLED' },
    { title: 'Visitas', file: 'visits', status: 'Status da visita', option: 'Realizada', value: 'COMPLETED' },
    { title: 'Acompanhantes', file: 'companions', status: 'Status da visita', option: 'Cancelada', value: 'CANCELLED' },
    { title: 'Acessos', file: 'access', status: 'Resultado', option: 'Negado', value: 'DENIED' },
  ])('$title manda só os filtros preenchidos, com o status do próprio arquivo', async ({ title, file, status, option, value }) => {
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const user = userEvent.setup()
    const { urls } = renderExports(() => csv(`${file}.csv`))
    const region = await screen.findByRole('region', { name: title })
    await within(region).findByRole('option', { name: 'Ana Prospectora (P-01)' })

    fireEvent.change(within(region).getByLabelText('De'), { target: { value: '2026-09-01' } })
    fireEvent.change(within(region).getByLabelText('Até'), { target: { value: '2026-09-24' } })
    await user.selectOptions(within(region).getByLabelText(status), option)
    await user.selectOptions(within(region).getByLabelText('Prospector'), 'p-1')
    await user.click(within(region).getByRole('button', { name: 'Baixar CSV' }))

    await waitFor(() =>
      expect(urls).toEqual([`/api/exports/${file}?from=2026-09-01&to=2026-09-24&status=${value}&prospectorId=p-1`]),
    )
  })

  it('cada bloco oferece só os status do próprio arquivo', async () => {
    renderExports()
    await screen.findByRole('region', { name: 'Acessos' })

    const options = (title: string, label: string) =>
      within(within(block(title)).getByLabelText(label)).getAllByRole('option').map((o) => o.textContent)
    expect(options('Leads', 'Status do Lead')).toEqual(['Todos', 'Novo', 'Contatado', 'Visita agendada', 'Visitou', 'Descartado'])
    expect(options('Visitas', 'Status da visita')).toEqual(['Todos', 'Agendada', 'Realizada', 'Cancelada', 'Não compareceu'])
    expect(options('Acessos', 'Resultado')).toEqual(['Todos', 'Liberado', 'Negado'])
  })

  it('período invertido ou com uma data só é bloqueado, sem pedido', async () => {
    const { urls } = renderExports()
    const region = await screen.findByRole('region', { name: 'Visitas' })

    fireEvent.change(within(region).getByLabelText('De'), { target: { value: '2026-09-10' } })
    expect(within(region).getByRole('alert')).toHaveTextContent('Informe as duas datas do período, ou nenhuma.')
    expect(within(region).getByRole('button', { name: 'Baixar CSV' })).toBeDisabled()

    fireEvent.change(within(region).getByLabelText('Até'), { target: { value: '2026-09-01' } })
    expect(within(region).getByRole('alert')).toHaveTextContent('A data inicial não pode ser posterior à final.')
    expect(within(region).getByRole('button', { name: 'Baixar CSV' })).toBeDisabled()
    expect(urls).toEqual([])
  })

  it.each([
    [problem(404, 'PROSPECTOR_NOT_FOUND'), 'Prospector não encontrado.'],
    [problem(400, 'VALIDATION_ERROR', 'A data inicial não pode ser posterior à final.'), 'A data inicial não pode ser posterior à final.'],
    [problem(500, 'ERROR'), 'Não foi possível concluir a operação.'],
  ])('erro %# aparece em português no bloco', async (response, message) => {
    const user = userEvent.setup()
    renderExports(() => response)

    const region = await screen.findByRole('region', { name: 'Acompanhantes' })
    await user.click(within(region).getByRole('button', { name: 'Baixar CSV' }))

    expect(await within(region).findByRole('alert')).toHaveTextContent(message)
  })
})
