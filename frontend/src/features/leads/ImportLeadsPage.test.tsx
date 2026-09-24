import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { me, mockFetch, renderApp } from '@/test/utils'

function csvFile() {
  return new File(['nome;cpf;telefone;email;data_nascimento;codigo_prospector;observacoes\nLead Fictício;;;;;;\n'], 'leads.csv', {
    type: 'text/csv',
  })
}

async function upload() {
  const user = userEvent.setup()
  await user.upload(await screen.findByLabelText('Arquivo'), csvFile())
  await user.click(screen.getByRole('button', { name: 'Importar' }))
}

describe('ImportLeadsPage', () => {
  it('mostra a quantidade importada', async () => {
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (method === 'POST' && url === '/api/leads/import') return { body: { imported: 120, totalRows: 120 } }
    })
    renderApp('/leads/importar')

    await upload()

    expect(await screen.findByRole('status')).toHaveTextContent('120 Leads importados.')
  })

  it('mostra o relatório por linha quando o arquivo é recusado', async () => {
    mockFetch((method, url) => {
      if (url === '/api/auth/me') return { body: me('ADMIN') }
      if (method === 'POST' && url === '/api/leads/import') {
        return {
          status: 422,
          body: {
            status: 422,
            code: 'IMPORT_REJECTED',
            detail: 'O arquivo tem 2 erros. Nenhum Lead foi importado.',
            totalRows: 3,
            errors: [
              { line: 3, column: 'cpf', code: 'CPF_INVALID', message: 'CPF inválido.' },
              { line: 5, column: null, code: 'COLUMN_COUNT_MISMATCH', message: 'A linha tem 6 colunas; esperado 7.' },
            ],
          },
        }
      }
    })
    renderApp('/leads/importar')

    await upload()

    expect(await screen.findByRole('alert')).toHaveTextContent('Nenhum Lead foi importado.')
    const rows = within(screen.getByRole('table', { name: 'Erros da importação' })).getAllByRole('row').slice(1)
    expect(rows.map((row) => row.textContent)).toEqual(['3CPFCPF inválido.', '5—A linha tem 6 colunas; esperado 7.'])
  })

  it('o link do modelo aponta para o template do backend', async () => {
    mockFetch((_method, url) => (url === '/api/auth/me' ? { body: me('ADMIN') } : undefined))
    renderApp('/leads/importar')

    expect(await screen.findByRole('link', { name: 'Baixar modelo' })).toHaveAttribute('href', '/api/leads/import/template')
  })
})
