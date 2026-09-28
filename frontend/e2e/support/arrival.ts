import { fakeCpf, operationDate, recordSensitive } from './data.ts'
import type { Person, World } from './fixtures.ts'

/** Observações de 2.000 caracteres, o máximo do campo (D-097), em palavras, como um texto real. */
export const LONGEST_HOST_NOTES = (() => {
  const words = 'Prefere conhecer as piscinas e o restaurante antes da apresentação, com atenção às crianças. '
  return words.repeat(Math.ceil(2000 / words.length)).slice(0, 1999) + '.'
})()

const COMPANIONS = [
  { name: 'Maria Aparecida Fictícia dos Santos Oliveira', relationship: 'SPOUSE', birthDate: '1982-03-15' },
  { name: 'João Pedro Fictício dos Santos Oliveira', relationship: 'CHILD', birthDate: '2012-07-02' },
  { name: 'Ana Beatriz Fictícia dos Santos Oliveira', relationship: 'CHILD', birthDate: '2016-11-20' },
  { name: 'José Carlos Fictício de Oliveira', relationship: 'FATHER', birthDate: '1950-01-09' },
  { name: 'Helena Fictícia de Oliveira Souza', relationship: 'MOTHER', birthDate: '1953-05-30' },
  { name: 'Carolina Fictícia Amiga da Família', relationship: 'FRIEND', birthDate: '1985-09-12' },
]

/**
 * Pior caso da ficha (D-097): visita de hoje com 6 acompanhantes (o padrão de APP_MAX_COMPANIONS) e
 * 2.000 caracteres de observações para o anfitrião, com a entrada registrada pela API (4 presentes e
 * 2 ausentes, para as duas listas aparecerem). Devolve o anfitrião e o id da visita.
 */
export async function worstCaseArrival(world: World): Promise<{ host: Person; visitId: string }> {
  const prospector = await world.createUser('PROSPECTOR')
  const gate = await world.createUser('GATE')
  const host = await world.createUser('HOST')
  const lead = await world.createLead({
    name: `Lead E2E Ficha Pior Caso ${world.suffix}`,
    cpf: fakeCpf(),
    phone: '(11) 90000-0004',
    birthDate: '1980-04-18',
    prospectorId: prospector.prospectorId!,
  })
  const prospectorApi = await world.apiAs(prospector)
  const visit = await prospectorApi.json<{ id: string; invitation: { id: string } }>('POST', '/api/visits', {
    leadId: lead.id,
    scheduledDate: operationDate(),
    notes: null,
    hostNotes: LONGEST_HOST_NOTES,
    companions: COMPANIONS.map((companion) => ({ ...companion, cpf: null })),
  })
  const invitation = (await (await prospectorApi.get(`/api/invitations/${visit.invitation.id}`)).json()) as { code: string }
  recordSensitive(invitation.code)
  const gateApi = await world.apiAs(gate)
  const validated = await gateApi.json<{ result: string; invitationId: string; companions: { id: string }[] }>(
    'POST',
    '/api/access/validate',
    { code: invitation.code },
  )
  if (validated.result !== 'AUTHORIZED') throw new Error(`validação: ${validated.result}`)
  await gateApi.json('POST', '/api/access/register', {
    invitationId: validated.invitationId,
    presentCompanionIds: validated.companions.slice(0, 4).map((companion) => companion.id),
  })
  return { host, visitId: visit.id }
}
