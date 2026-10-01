export interface Series<K extends string> {
  key: K
  label: string
  /** Cor fixa da série (D-100): segue o significado, nunca a posição. */
  color: string
}

/**
 * Cores pelo significado (D-100, revisada na v0.3.0), todas da paleta de referência: realizadas no azul do
 * slot 1 (`#2a78d6`); agendadas, que são futuras, no azul claro da rampa sequencial (passo 250, `#86b6ef`);
 * sem comparecimento no laranja do slot 2 (`#eb6834`); cancelamentos no cinza neutro (`#898781`). Fora do
 * tema do cliente (D-120). Validado para daltônicos em todos os pares; as duas cores de pouca saturação
 * são de propósito (não são resultado), e a legenda, o tooltip e a tabela dizem a série.
 */
export const VISIT_SERIES: Series<'scheduled' | 'completed' | 'noShow' | 'cancelled'>[] = [
  { key: 'scheduled', label: 'Agendadas', color: '#86b6ef' },
  { key: 'completed', label: 'Realizadas', color: '#2a78d6' },
  { key: 'noShow', label: 'Sem comparecimento', color: '#eb6834' },
  { key: 'cancelled', label: 'Cancelamentos', color: '#898781' },
]
