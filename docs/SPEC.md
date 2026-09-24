# Especificação Técnica e Arquitetural — Sistema de Gestão de Leads, Visitas e Convites

**Versão:** 1.1
**Status:** Especificação da V1 (revisada)
**Substitui:** versão 1.0
**Decisões:** toda mudança em relação à 1.0 está justificada em `docs/DECISIONS.md` (referências no formato D-XXX).

Objetivo deste documento: definir o sistema com precisão suficiente para que o agente de desenvolvimento implemente a V1 sem ampliar o escopo por conta própria.

---

# 1. Visão do produto

Plataforma web para apoiar a operação comercial de um Resort na gestão de Leads, Prospectores, visitas agendadas, acompanhantes, convites e controle de acesso.

```text
LISTA DE LEADS (importação CSV)
      ↓
ADMIN atribui aos PROSPECTORES
      ↓
PROSPECTOR contata o Lead
      ↓
AGENDAMENTO DA VISITA (+ acompanhantes + observações para o anfitrião)
      ↓
CONVITE com código único e QR Code
      ↓
PORTARIA valida o código
      ↓
ENTRADA REGISTRADA (Lead + acompanhantes presentes)
      ↓
ANFITRIÃO recebe a ficha da visita
      ↓
VISITA REALIZADA
      ↓
EXPORTAÇÃO CSV para o sistema comercial do Resort
```

O sistema **não é um CRM** e **não gerencia a conversão comercial** do Lead em Cliente/Sócio/Associado. O resultado da apresentação e da venda pertence ao processo e ao sistema comercial já existentes no Resort.

---

# 2. Objetivo da V1

1. Organizar os Leads atribuídos aos Prospectores.
2. Permitir o agendamento da visita, com acompanhantes, e a geração do convite.
3. Permitir que a Portaria valide o convite rapidamente e registre a entrada.
4. Entregar ao anfitrião a ficha da visita (Lead e acompanhantes) no momento da chegada.
5. Permitir que gestores acompanhem a operação e exportem os dados.

Prioridades: simplicidade operacional, confiabilidade e rastreabilidade.

---

# 3. Escopo da V1

## Incluído

- Autenticação por sessão e troca de senha.
- Usuários e perfis de acesso (ADMIN, PROSPECTOR, GATE, HOST).
- Prospectores.
- Leads, com importação por CSV.
- Atribuição e reatribuição de Leads.
- Agendamento, remarcação e cancelamento de visitas.
- Acompanhantes da visita.
- Convites com código único e QR Code; reemissão de código.
- Expiração automática de convites.
- Validação na Portaria e registro de entrada, inclusive dos acompanhantes presentes.
- Tela de chegadas do dia e ficha da visita para o anfitrião, com versão para impressão.
- Dashboards operacionais.
- Filtros e paginação.
- Exportação CSV.
- Auditoria de ações críticas.
- Responsividade para desktop, tablet e celular.

## Fora do escopo

Não implementar na V1:

- CRM, pipeline comercial, gestão de vendas, registro de resultado da apresentação.
- Contratos, pagamentos, comissões, fidelidade.
- Gestão de Sócios/Associados, hospedagem, reservas.
- Recuperação de senha por e-mail (D-016).
- Tela de configuração de parâmetros; parâmetros vêm do ambiente (D-026).
- Atribuição de anfitrião a visitas (D-023).
- Marketplace, reconhecimento facial, aplicativo nativo, WhatsApp oficial.
- Integração automática com o sistema comercial existente.
- Microsserviços, Kubernetes, BI avançado, WebSockets.

A arquitetura deve permitir evolução, mas nada do que está fora do escopo deve ser implementado antecipadamente.

---

# 4. Perfis de usuário

Nomes no código: `ADMIN`, `PROSPECTOR`, `GATE`, `HOST`. Na interface: Administrador, Prospector, Portaria, Anfitrião (D-011).

## 4.1 ADMIN

- gerenciar usuários e Prospectores; redefinir senhas;
- importar, visualizar, criar, editar e atribuir Leads;
- reativar Leads cancelados;
- visualizar e operar todas as visitas e convites;
- visualizar acessos, chegadas e fichas;
- visualizar dashboard, exportar dados e consultar auditoria.

## 4.2 PROSPECTOR

- visualizar e editar os Leads da própria carteira;
- marcar Lead como contatado ou descartado;
- agendar, editar, remarcar e cancelar visitas dos seus Leads;
- cadastrar e editar acompanhantes;
- visualizar, compartilhar e reemitir convites das suas visitas;
- ver as chegadas do dia **das suas visitas** e a ficha delas, pois muitas vezes o próprio Prospector é o anfitrião (D-023);
- consultar o histórico das suas visitas.

Não pode acessar dados de outros Prospectores, usuários, auditoria ou exportações.

## 4.3 GATE (Portaria)

- validar convite por QR Code ou código digitado;
- ver os dados mínimos para conferência (seção 15);
- confirmar a entrada e marcar os acompanhantes presentes;
- consultar os acessos recentes.

Não pode criar ou editar Leads, visitas, convites ou usuários.

## 4.4 HOST (Anfitrião)

Perfil de leitura para quem recebe e apresenta o Resort sem ser o Prospector.

- ver as chegadas do dia (todas);
- abrir e imprimir a ficha das visitas que chegaram no dia.

Não vê Leads fora das chegadas do dia, não edita nada e não registra resultado de venda.

## 4.5 Matriz resumida

| Recurso | ADMIN | PROSPECTOR | GATE | HOST |
|---|---|---|---|---|
| Usuários / Prospectores | total | — | — | — |
| Leads | total | própria carteira | — | — |
| Importação / atribuição | sim | — | — | — |
| Visitas e acompanhantes | total | visitas dos seus Leads | — | — |
| Convites | total | das suas visitas | — | — |
| Validar / registrar entrada | — | — | sim | — |
| Acessos recentes | sim | — | sim | — |
| Chegadas do dia e ficha | todas | das suas visitas | — | todas |
| Dashboard | geral | próprio | — | — |
| Exportação / auditoria | sim | — | — | — |

---

# 5. Conceitos do domínio

- **Prospector:** funcionário comercial que recebe Leads, faz o contato e agenda a visita.
- **Lead:** pessoa potencialmente interessada em conhecer o Resort. Pode ter várias visitas ao longo do tempo, mas apenas uma agendada por vez.
- **Visita:** compromisso de um Lead em uma data. Origina o convite e agrupa os acompanhantes.
- **Acompanhante:** pessoa que vem junto com o Lead em uma visita específica (D-020).
- **Convite:** autorização para a visita em uma data. Possui um código único, que também é o conteúdo do QR Code.
- **Acesso:** registro de cada tentativa de validação na Portaria, autorizada ou negada.
- **Anfitrião:** quem recebe o Lead e faz a apresentação. Pode ser o próprio Prospector ou um usuário HOST.
- **Ficha da visita:** resumo de leitura do Lead e dos acompanhantes, entregue ao anfitrião na chegada.
- **Cliente/Sócio/Associado:** fora do domínio da V1.

---

# 6. Fluxos

## 6.1 Preparação

1. ADMIN importa Leads por CSV (seção 17) ou cadastra manualmente.
2. ADMIN atribui Leads aos Prospectores, individualmente ou em lote.
3. Prospector visualiza sua carteira.

## 6.2 Agendamento

1. Prospector abre o Lead e faz o contato (fora do sistema).
2. Opcionalmente marca o Lead como `CONTACTED`.
3. Ao agendar, informa a data. Pode, na mesma tela, preencher a seção opcional "Acompanhantes" e o campo "Observações para o anfitrião".
4. O sistema cria a visita e o convite na mesma transação e gera o código.
5. O convite fica disponível para visualização, download e compartilhamento.
6. Até o dia da visita, o Prospector pode completar acompanhantes e observações sem alterar o código.

## 6.3 Chegada

1. A Portaria escaneia o QR ou digita o código.
2. O backend valida (seção 12) e devolve o resultado.
3. Se negado: tela de acesso negado com o motivo. A tentativa fica registrada.
4. Se liberado: tela mostra Lead, data, Prospector e a lista de acompanhantes, todos marcados. O porteiro desmarca quem não veio e confirma a entrada.
5. A entrada é registrada; o convite passa a `USED`, a visita a `COMPLETED`, o Lead a `VISITED`.

## 6.4 Apresentação

1. A visita aparece em "Chegadas de hoje" para os HOSTs, para o ADMIN e para o Prospector responsável.
2. A lista é atualizada por polling a cada 30 segundos.
3. O anfitrião abre a ficha no aparelho ou a imprime.

## 6.5 Encerramento do dia

Job noturno (seção 20) expira convites não utilizados e marca as visitas correspondentes como `NO_SHOW`.

---

# 7. Estados e transições

## 7.1 Enumerações

```text
LeadStatus:        NEW, CONTACTED, VISIT_SCHEDULED, VISITED, CANCELLED
VisitStatus:       SCHEDULED, COMPLETED, CANCELLED, NO_SHOW
InvitationStatus:  ACTIVE, USED, CANCELLED, EXPIRED
AccessResult:      AUTHORIZED, DENIED
DenialReason:      INVALID_CODE, CANCELLED, ALREADY_USED, EXPIRED, WRONG_DATE
Relationship:      SPOUSE, CHILD, FATHER, MOTHER, SIBLING, GRANDPARENT, GRANDCHILD, FRIEND, OTHER
Role:              ADMIN, PROSPECTOR, GATE, HOST
```

`CONVERTED` não existe na V1.

## 7.2 Transições

| Evento | Quem | Lead | Visita | Convite |
|---|---|---|---|---|
| Registrar contato | PROSPECTOR, ADMIN | NEW → CONTACTED | — | — |
| Agendar visita | PROSPECTOR, ADMIN | → VISIT_SCHEDULED | SCHEDULED | ACTIVE (novo) |
| Reemitir código | PROSPECTOR, ADMIN | — | — | atual → CANCELLED; novo ACTIVE |
| Cancelar visita | PROSPECTOR, ADMIN | → CONTACTED | CANCELLED | → CANCELLED |
| Remarcar | PROSPECTOR, ADMIN | cancelar + agendar, na mesma transação | antiga CANCELLED; nova SCHEDULED | antigo CANCELLED; novo ACTIVE |
| Registrar entrada | GATE | → VISITED | COMPLETED | USED |
| Job noturno | sistema | → CONTACTED | NO_SHOW | EXPIRED |
| Descartar Lead | PROSPECTOR, ADMIN | → CANCELLED | visita SCHEDULED é cancelada | → CANCELLED |
| Reativar Lead | ADMIN | CANCELLED → NEW | — | — |

`CANCELLED` no Lead significa inativo (D-008). Transições não listadas devem ser rejeitadas com erro 409.

---

# 8. Modelo de dados

Convenções: tabelas em snake_case no plural; chaves primárias `UUID` (D-027); datas-hora em `timestamptz` (UTC); datas de negócio em `date`; enums como `varchar` com `CHECK`.

## 8.1 users

```text
id                    uuid pk
name                  varchar(120) not null
email                 varchar(160) not null unique (armazenado em minúsculas)
password_hash         varchar(100) not null
role                  varchar(20)  not null
active                boolean not null default true
must_change_password  boolean not null default false
created_at, updated_at
```

## 8.2 prospectors

```text
id             uuid pk
user_id        uuid not null unique fk users
employee_code  varchar(30) not null unique
phone          varchar(20)
created_at, updated_at
```

Nome, e-mail e situação ativa vêm de `users` (D-014).

## 8.3 leads

```text
id             uuid pk
name           varchar(120) not null
cpf            char(11) null            -- somente dígitos
phone          varchar(20)
email          varchar(160)
birth_date     date
notes          text                     -- notas internas do Prospector
status         varchar(20) not null default 'NEW'
prospector_id  uuid null fk prospectors -- null = não atribuído
created_at, updated_at

unique (cpf) where cpf is not null
index (prospector_id, status)
```

## 8.4 visits

```text
id              uuid pk
lead_id         uuid not null fk leads
prospector_id   uuid not null fk prospectors -- responsável no momento do agendamento; imutável
scheduled_date  date not null
status          varchar(20) not null default 'SCHEDULED'
notes           text             -- notas operacionais
host_notes      text             -- observações para o anfitrião (D-024)
cancelled_at    timestamptz
created_at, updated_at

unique (lead_id) where status = 'SCHEDULED'
index (scheduled_date, status)
```

## 8.5 visit_companions

```text
id            uuid pk
visit_id      uuid not null fk visits on delete cascade
name          varchar(120) not null
cpf           char(11) null
birth_date    date not null
relationship  varchar(20) not null
created_at, updated_at
```

## 8.6 invitations

```text
id            uuid pk
visit_id      uuid not null fk visits
code          char(10) not null unique -- Crockford Base32, sem hífen (D-001, D-003)
status        varchar(20) not null default 'ACTIVE'
expires_at    timestamptz not null     -- fim do dia da visita no timezone da operação
used_at       timestamptz
cancelled_at  timestamptz
created_at, updated_at

unique (visit_id) where status = 'ACTIVE'
```

Lead e Prospector são obtidos pela visita (D-013).

## 8.7 access_records

```text
id                    uuid pk
invitation_id         uuid null fk invitations -- null quando INVALID_CODE
attempted_code        varchar(20) not null     -- valor normalizado recebido
validated_by_user_id  uuid not null fk users
gate                  varchar(40) not null     -- APP_GATE_NAME
result                varchar(20) not null
denial_reason         varchar(20) null
entry_at              timestamptz null         -- preenchido quando AUTHORIZED
created_at            timestamptz not null
```

## 8.8 access_record_companions

```text
access_record_id  uuid fk access_records
companion_id      uuid fk visit_companions
primary key (access_record_id, companion_id)
```

Registra quais acompanhantes efetivamente entraram (D-021).

## 8.9 audit_logs

```text
id           uuid pk
user_id      uuid null fk users  -- null para ações do sistema (job)
action       varchar(40) not null
entity_type  varchar(40)
entity_id    uuid
metadata     jsonb               -- sem CPF, senha ou dados pessoais desnecessários
ip_address   varchar(45)
created_at   timestamptz not null
```

Tabela somente de inserção, protegida por trigger que impede UPDATE e DELETE (D-028).

## 8.10 Sessões

Tabelas do Spring Session JDBC, criadas por migration Flyway a partir do schema oficial para PostgreSQL (D-015).

---

# 9. Relacionamentos

```text
USER 1:1 PROSPECTOR (somente role PROSPECTOR)
PROSPECTOR 1:N LEAD
LEAD 1:N VISIT                 (no máximo 1 SCHEDULED)
PROSPECTOR 1:N VISIT
VISIT 1:N VISIT_COMPANION
VISIT 1:N INVITATION           (no máximo 1 ACTIVE)
INVITATION 1:N ACCESS_RECORD
ACCESS_RECORD N:N VISIT_COMPANION (presença)
```

---

# 10. Regras de negócio

- **RN01 — Lead inativo.** Lead `CANCELLED` não recebe visita. Só o ADMIN reativa.
- **RN02 — Carteira.** Prospector só opera Leads com `prospector_id` igual ao seu. Verificação sempre no backend.
- **RN03 — Visita exige Lead atribuído.** `visits.prospector_id` recebe o Prospector responsável no momento do agendamento e não muda depois.
- **RN04 — Uma visita agendada por Lead.** Garantido por índice parcial; violação retorna 409.
- **RN05 — Data.** `scheduled_date` deve ser hoje ou futura, no timezone da operação.
- **RN06 — Convite nasce com a visita.** Criado na mesma transação, com status `ACTIVE`.
- **RN07 — Código.** 10 caracteres do alfabeto Crockford Base32, gerados por `SecureRandom`, únicos. Em colisão, gerar outro.
- **RN08 — QR.** Contém apenas `RSV:` seguido do código. Nenhum dado pessoal.
- **RN09 — Validade.** O convite só libera acesso na `scheduled_date`, no timezone da operação.
- **RN10 — Bloqueios.** Convite cancelado, usado ou expirado nunca libera acesso.
- **RN11 — Entrada.** Registro em transação única com lock pessimista: `AccessRecord AUTHORIZED`, presença dos acompanhantes, convite `USED`, visita `COMPLETED`, Lead `VISITED`.
- **RN12 — Negativas.** Toda validação negada gera `AccessRecord DENIED` e auditoria.
- **RN13 — Acompanhantes.** Máximo definido por `APP_MAX_COMPANIONS` (padrão 6). Nome, data de nascimento e parentesco obrigatórios; CPF opcional, validado quando informado. Editáveis enquanto a visita estiver `SCHEDULED`.
- **RN14 — CPF.** Somente dígitos, com validação dos dígitos verificadores. Único entre Leads quando informado. Visibilidade conforme seção 15.
- **RN15 — Reatribuição.** Só ADMIN. Altera `leads.prospector_id`; não altera visitas existentes.
- **RN16 — Sem dados comerciais.** O sistema não infere nem registra conversão, venda ou resultado da apresentação.
- **RN17 — Exportação.** Só ADMIN, em CSV, sempre auditada.
- **RN18 — Importação.** Tudo ou nada (seção 17).
- **RN19 — Senhas.** BCrypt; mínimo de 10 caracteres; troca obrigatória após criação de usuário ou redefinição pelo ADMIN.
- **RN20 — Auditoria.** Imutável.

---

# 11. API

REST sob `/api`, JSON, paginação `page` e `size` (padrão 20, máximo 100). Erros no formato Problem Details (RFC 9457) (D-033). Toda escrita exige token CSRF.

## Auth

```http
POST /api/auth/login              público
POST /api/auth/logout             autenticado
GET  /api/auth/me                 autenticado
POST /api/auth/change-password    autenticado
```

## Usuários

```http
GET    /api/users                        ADMIN
GET    /api/users/{id}                   ADMIN
POST   /api/users                        ADMIN   (role PROSPECTOR exige employee_code; cria o prospector na mesma transação)
PUT    /api/users/{id}                   ADMIN
PATCH  /api/users/{id}/status            ADMIN
POST   /api/users/{id}/reset-password    ADMIN   (retorna senha temporária uma única vez)
```

## Prospectores

```http
GET    /api/prospectors          ADMIN
GET    /api/prospectors/{id}     ADMIN
PUT    /api/prospectors/{id}     ADMIN   (employee_code, phone)
```

Ativação e desativação são feitas em `/api/users/{id}/status`.

## Leads

```http
GET    /api/leads                    ADMIN, PROSPECTOR (filtrado)
GET    /api/leads/{id}               ADMIN, PROSPECTOR (própria carteira)
POST   /api/leads                    ADMIN
PUT    /api/leads/{id}               ADMIN, PROSPECTOR (própria carteira)
PATCH  /api/leads/{id}/status        ADMIN, PROSPECTOR (transições da seção 7.2)
PATCH  /api/leads/assign             ADMIN   { leadIds[], prospectorId }
POST   /api/leads/import             ADMIN   multipart CSV
GET    /api/leads/import/template    ADMIN
```

## Visitas

```http
GET    /api/visits                   ADMIN, PROSPECTOR (filtrado)
GET    /api/visits/{id}              ADMIN, PROSPECTOR
POST   /api/visits                   ADMIN, PROSPECTOR  { leadId, scheduledDate, notes, hostNotes, companions[] }
PUT    /api/visits/{id}              ADMIN, PROSPECTOR  { notes, hostNotes, companions[] } (substitui a lista)
POST   /api/visits/{id}/reschedule   ADMIN, PROSPECTOR  { scheduledDate }
PATCH  /api/visits/{id}/cancel       ADMIN, PROSPECTOR
GET    /api/visits/{id}/sheet        ADMIN, PROSPECTOR, HOST (seção 16.6)
```

## Convites

```http
GET    /api/invitations                ADMIN, PROSPECTOR (filtrado)
GET    /api/invitations/{id}           ADMIN, PROSPECTOR
GET    /api/invitations/{id}/qr-code   ADMIN, PROSPECTOR  (PNG)
POST   /api/invitations/{id}/reissue   ADMIN, PROSPECTOR
```

Não há `POST /api/invitations` nem cancelamento isolado de convite (D-010).

## Acesso

```http
POST   /api/access/validate   GATE  { code }
POST   /api/access/register   GATE  { invitationId, presentCompanionIds[] }
GET    /api/access/recent     GATE, ADMIN
```

## Chegadas

```http
GET    /api/arrivals?date=YYYY-MM-DD   ADMIN, HOST (todas), PROSPECTOR (suas visitas); padrão: hoje
```

HOST só pode consultar a data de hoje.

## Dashboard

```http
GET /api/dashboard/summary         ADMIN (geral), PROSPECTOR (próprio)
GET /api/dashboard/visits-by-day   ADMIN, PROSPECTOR
GET /api/dashboard/access-by-day   ADMIN
```

## Exportação

```http
GET /api/exports/leads        ADMIN
GET /api/exports/visits       ADMIN
GET /api/exports/companions   ADMIN
GET /api/exports/access       ADMIN
```

## Auditoria

```http
GET /api/audit   ADMIN   filtros: período, usuário, ação, entidade
```

## Saúde

```http
GET /actuator/health   público (somente status)
```

---

# 12. Validação e registro de acesso

## 12.1 Normalização do código

Recebido do QR ou digitado: remover o prefixo `RSV:`, espaços e hífens; converter para maiúsculas; mapear `O→0`, `I→1`, `L→1`. Código com tamanho ou caracteres inválidos resulta em `INVALID_CODE`.

## 12.2 `POST /api/access/validate`

Somente leitura sobre o convite. Ordem das verificações:

```text
1. convite com o código não existe      → DENIED / INVALID_CODE
2. status CANCELLED                     → DENIED / CANCELLED
3. status USED                          → DENIED / ALREADY_USED
4. status EXPIRED ou hoje > data        → DENIED / EXPIRED
5. hoje < data                          → DENIED / WRONG_DATE
6. caso contrário                       → liberado (nada é gravado)
```

"Hoje" é calculado no timezone `APP_TIMEZONE`. Negativas gravam `AccessRecord DENIED` e auditoria `ACCESS_DENIED`.

Resposta liberada: `invitationId`, nome do Lead, data, nome do Prospector, lista de acompanhantes (id, nome, parentesco).

## 12.3 `POST /api/access/register`

Transação única:

```text
1. SELECT convite FOR UPDATE
2. reexecutar as verificações 2 a 5
3. se falhar → AccessRecord DENIED e retorno do motivo (ex.: clique duplo → ALREADY_USED)
4. validar que presentCompanionIds pertencem à visita
5. inserir AccessRecord AUTHORIZED com entry_at = agora
6. inserir access_record_companions
7. convite → USED, used_at = agora
8. visita → COMPLETED; Lead → VISITED
9. auditoria ACCESS_VALIDATED
```

## 12.4 Limite de tentativas

`validate` limitado a 30 chamadas por minuto por usuário GATE (D-017).

---

# 13. Código do convite e QR Code

- Alfabeto Crockford Base32: `0123456789ABCDEFGHJKMNPQRSTVWXYZ`.
- 10 caracteres (cerca de 50 bits), exibidos como `ABCDE-FGHJK`.
- Armazenado sem hífen, em claro, com índice único (D-003).
- QR com conteúdo `RSV:ABCDEFGHJK`, gerado no backend como PNG.
- A imagem de compartilhamento é montada no frontend (canvas) com nome do Lead, data, código formatado e QR. "Compartilhar" usa a Web Share API quando disponível; caso contrário, faz download.
- Reemissão: cancela o convite atual e cria outro para a mesma visita, com novo código. Uso típico: código enviado à pessoa errada.

---

# 14. Segurança

## Autenticação (D-015)

- Sessão no servidor (Spring Security + Spring Session JDBC), cookie `HttpOnly`, `Secure` em produção, `SameSite=Lax`.
- CSRF com `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` lido pelo frontend e enviado no header).
- Tempo de inatividade configurável (`APP_SESSION_TIMEOUT`, padrão 8h).
- Logout invalida a sessão no servidor.
- Usuário com `must_change_password` só acessa `/api/auth/me`, `/api/auth/change-password` e `/api/auth/logout`.
- Limite de login: 5 falhas por combinação e-mail + IP em 15 minutos, em memória (D-017).
- Primeiro ADMIN criado na inicialização a partir de `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD`, somente se não existir nenhum ADMIN, com troca obrigatória de senha (D-032).

## Autorização

RBAC com `ADMIN`, `PROSPECTOR`, `GATE`, `HOST`, sempre no backend: por rota (configuração de segurança) e por recurso (verificação de carteira nos services). Acesso a recurso de outro Prospector retorna 404, sem revelar existência.

## API e infraestrutura

- Validação de payload com Bean Validation.
- Frontend e API na mesma origem atrás do Nginx; sem CORS em produção (D-029).
- Headers de segurança no Nginx: HSTS, `X-Content-Type-Options`, `Referrer-Policy`, CSP restritiva.
- Logs sem CPF, senha, código de convite completo ou corpo de requisição.

---

# 15. Dados pessoais e visibilidade

Princípio de minimização. Finalidade do tratamento dos dados de acompanhantes, incluindo menores, registrada em D-022.

| Campo | ADMIN | PROSPECTOR (carteira) | GATE | HOST / ficha |
|---|---|---|---|---|
| CPF do Lead | completo | mascarado `***.456.789-**` | — | — |
| Telefone do Lead | sim | sim | — | sim |
| E-mail do Lead | sim | sim | — | — |
| Nascimento do Lead | data | data | — | idade |
| Notas internas (`leads.notes`) | sim | sim | — | — |
| Observações para o anfitrião | sim | sim | — | sim |
| Acompanhante: nome e parentesco | sim | sim | sim (na liberação) | sim |
| Acompanhante: CPF | completo | mascarado | — | — |
| Acompanhante: nascimento | data | data | — | idade |

O mascaramento é feito no backend, no DTO. O frontend nunca recebe o dado que não pode exibir.

---

# 16. Frontend

## 16.1 Navegação por perfil

```text
ADMIN:       Dashboard, Leads, Prospectores, Visitas, Convites, Chegadas, Acessos, Exportações, Usuários, Auditoria
PROSPECTOR:  Dashboard, Meus Leads, Agenda, Convites, Chegadas de hoje, Histórico, Perfil
GATE:        Validar Convite, Acessos Recentes
HOST:        Chegadas de hoje
```

Após login, cada perfil cai direto na sua tela principal. GATE e HOST não têm dashboard.

## 16.2 Tela do Lead

Dados conforme seção 15, Prospector responsável, status, visita agendada (se houver), convite, histórico de visitas.

Ações: Agendar visita, Registrar contato, Ver convite, Remarcar, Cancelar visita, Descartar Lead.

## 16.3 Agendamento

```text
Lead selecionado
      ↓
Data da visita
      ↓
[opcional] Acompanhantes  (nome, parentesco, nascimento, CPF opcional; botão "adicionar")
      ↓
[opcional] Observações para o anfitrião
      ↓
Confirmar → convite gerado → tela do convite
```

As seções opcionais começam recolhidas. O fluxo mínimo é: data e confirmar.

## 16.4 Convite

```text
CONVITE
Nome do Lead
Data da visita
Prospector
Acompanhantes: N
Status
[ QR CODE ]
ABCDE-FGHJK
[ Compartilhar ] [ Baixar ] [ Reemitir código ] [ Cancelar visita ]
```

## 16.5 Portaria

Otimizada para velocidade e uso em celular ou tablet, com botões grandes.

```text
VALIDAR CONVITE
[ ESCANEAR QR CODE ]
ou
[ DIGITAR CÓDIGO ]   (campo com máscara XXXXX-XXXXX)
```

Liberado:

```text
ACESSO LIBERADO
Nome · Data · Prospector
Acompanhantes (todos marcados; desmarcar quem não veio)
[ CONFIRMAR ENTRADA ]
```

Negado:

```text
ACESSO NEGADO
Motivo em linguagem clara
[ NOVA VALIDAÇÃO ]
```

Após confirmar ou negar, a tela volta ao scanner. Scanner com `@zxing/browser` (D-030).

## 16.6 Chegadas de hoje e ficha da visita

Lista das entradas do dia, mais recentes primeiro: horário de entrada, nome do Lead, número de acompanhantes presentes, Prospector. Atualização por polling de 30 segundos.

Ficha:

```text
FICHA DA VISITA — data
Lead: nome, idade, telefone
Prospector: nome
Horário de entrada
Acompanhantes presentes: nome, parentesco, idade
Acompanhantes ausentes: nome, parentesco
Observações para o anfitrião
[ Imprimir ]
```

Impressão por CSS `@media print`, em uma página A4, sem biblioteca de PDF (D-025).

## 16.7 Dashboards

Prospector: Meus Leads, Visitas agendadas, Visitas hoje, Convites ativos, Visitas realizadas; lista de próximas visitas.

Admin: Total de Leads, Leads atribuídos, Visitas agendadas, Visitas hoje, Convites ativos, Visitas realizadas, No-show, Cancelamentos, Entradas realizadas. Filtros: período, Prospector, status. Gráficos simples de visitas e entradas por dia.

## 16.8 Idioma e formatos

Interface em português do Brasil. Datas `DD/MM/AAAA`. Código-fonte em inglês.

---

# 17. Importação de Leads

- CSV com separador `;`, UTF-8 (com ou sem BOM), cabeçalho obrigatório.
- Colunas: `nome;cpf;telefone;email;data_nascimento;codigo_prospector;observacoes`.
- Obrigatório: `nome`. Demais opcionais. `data_nascimento` em `DD/MM/AAAA`.
- `codigo_prospector`, quando preenchido, deve existir e estar ativo; o Lead já nasce atribuído.
- Erros: CPF inválido, CPF já existente no banco, CPF duplicado no arquivo, data inválida, e-mail inválido, Prospector inexistente.
- Tudo ou nada: com qualquer erro, nada é importado e a resposta traz o relatório por linha (D-018).
- Limite: 5.000 linhas por arquivo.
- Auditoria `LEAD_IMPORTED` com a quantidade importada.

---

# 18. Exportação

- CSV com separador `;`, UTF-8 com BOM, datas `DD/MM/AAAA` e horas `HH:mm`, para abrir corretamente no Excel em português (D-019).
- Filtros: período, Prospector, status.
- Arquivos:
  - `leads`: id, nome, CPF, telefone, e-mail, nascimento, status, Prospector, criado em.
  - `visits`: id, Lead, CPF do Lead, Prospector, data, status, quantidade de acompanhantes, entrada em.
  - `companions`: id da visita, Lead, nome, CPF, nascimento, parentesco, presente (sim/não).
  - `access`: data e hora, resultado, motivo, Lead, porteiro, portaria, acompanhantes presentes.
- Auditoria `EXPORT_GENERATED` com tipo e filtros.

---

# 19. Auditoria

Ações registradas:

```text
LOGIN, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGED, PASSWORD_RESET
USER_CREATED, USER_UPDATED, USER_STATUS_CHANGED
LEAD_CREATED, LEAD_UPDATED, LEAD_STATUS_CHANGED, LEAD_ASSIGNED, LEAD_IMPORTED
VISIT_CREATED, VISIT_UPDATED, VISIT_RESCHEDULED, VISIT_CANCELLED, VISIT_NO_SHOW
INVITATION_CREATED, INVITATION_REISSUED, INVITATION_CANCELLED, INVITATION_EXPIRED
ACCESS_VALIDATED, ACCESS_DENIED
EXPORT_GENERATED
```

Chamadas explícitas a um `AuditService` nos services, na mesma transação da ação. Sem AOP.

---

# 20. Job noturno

- `@Scheduled` diário às 00:15 no timezone `APP_TIMEZONE`.
- Convites `ACTIVE` com `expires_at` no passado → `EXPIRED`.
- Visitas correspondentes `SCHEDULED` → `NO_SHOW`; Leads → `CONTACTED`.
- Idempotente. Auditoria com `user_id` nulo.
- A validação na Portaria não depende do job (D-006).

---

# 21. Configuração

Variáveis de ambiente, com exemplo em `.env.example`:

```text
DB_URL, DB_USER, DB_PASSWORD
APP_TIMEZONE=America/Sao_Paulo
APP_MAX_COMPANIONS=6
APP_GATE_NAME=PRINCIPAL
APP_SESSION_TIMEOUT=8h
APP_BOOTSTRAP_ADMIN_EMAIL
APP_BOOTSTRAP_ADMIN_PASSWORD
```

Perfis Spring: `dev`, `test`, `prod`. Dados de exemplo somente no perfil `dev`.

---

# 22. Arquitetura e infraestrutura

## 22.1 Repositório

```text
/
├── CLAUDE.md
├── docs/ (SPEC.md, DECISIONS.md)
├── backend/     Spring Boot, Maven wrapper
├── frontend/    React + Vite
├── nginx/       configuração de produção
├── docker-compose.yml        desenvolvimento
├── docker-compose.prod.yml   produção
└── .env.example
```

## 22.2 Backend

Monólito modular. Pacote base `com.resort.platform`, módulos:

```text
auth, users, prospectors, leads, visits, invitations, access, arrivals, dashboard, exports, imports, audit, common
```

Camadas por módulo quando fizer sentido: controller, service, repository, entity, dto, mapper. Sem interfaces para services com implementação única.

## 22.3 Stack

- Backend: Java 21, Spring Boot 4.1.x (D-036), Spring Web MVC, Spring Security, Spring Session JDBC, Spring Data JPA, Bean Validation, Flyway, springdoc-openapi, ZXing (QR).
- Banco: PostgreSQL 16.
- Frontend: React, TypeScript, Vite, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS, shadcn/ui, Recharts, `@zxing/browser`.
- Testes: JUnit 5, Spring Boot Test, Testcontainers, Vitest, Testing Library, Playwright.

## 22.4 Ambientes

- Desenvolvimento: `docker-compose.yml` com `postgres` (volume persistente); backend e frontend rodando localmente ou em containers; Vite faz proxy de `/api` para o backend, mantendo a mesma origem.
- Produção: uma VPS com Docker Compose, Nginx servindo o build do frontend e fazendo proxy de `/api`, HTTPS com Let's Encrypt, `pg_dump` diário com retenção de 14 dias (D-029).

## 22.5 Migrations

Flyway, versionadas, nunca editadas depois de aplicadas. A lista abaixo é o conteúdo esperado; a numeração segue a ordem real de criação (D-034):

```text
V1__create_users.sql
V2__create_prospectors.sql
V3__create_leads.sql
V4__create_visits_and_companions.sql
V5__create_invitations.sql
V6__create_access_records.sql
V7__create_audit_logs.sql
V8__create_spring_session.sql
```

---

# 23. Testes

## Backend (unitários e integração com Testcontainers)

Autenticação, troca obrigatória de senha, limite de login, autorização por perfil e por carteira, CRUD e atribuição de Leads, importação (válida e com erros), criação de visita com acompanhantes, limite de acompanhantes, uma visita agendada por Lead, remarcação, cancelamento, geração e unicidade de código, reemissão, normalização de código, todas as negativas de acesso, registro de entrada com acompanhantes, **concorrência de registro** (duas chamadas simultâneas, apenas uma autorizada), job noturno, visibilidade de CPF por perfil, imutabilidade da auditoria.

## Frontend (Vitest)

Formulário de agendamento, máscara de código, montagem da ficha.

## E2E (Playwright)

```text
login → agendar visita com acompanhantes → ver convite
→ portaria valida código → confirma entrada com um acompanhante ausente
→ chegada aparece para o anfitrião → ficha correta
→ nova validação do mesmo código → ALREADY_USED
```

---

# 24. Critérios de aceite

## Autenticação

- usuário entra e sai; sessão é invalidada no logout;
- troca de senha obrigatória funciona;
- cada perfil acessa apenas o que a seção 4.5 permite.

## Prospector

- vê e opera apenas sua carteira;
- agenda visita com e sem acompanhantes;
- vê, baixa e compartilha o convite;
- remarca, reemite código e cancela;
- vê as chegadas das suas visitas e a ficha.

## Portaria

- valida por QR e por código digitado;
- convite válido é liberado; inválido, usado, cancelado, expirado ou fora da data é negado com o motivo correto;
- registra a entrada com os acompanhantes presentes;
- clique duplo ou chamadas simultâneas não geram duas entradas.

## Anfitrião

- vê as chegadas do dia em até 30 segundos após a entrada;
- abre e imprime a ficha;
- não acessa nada além disso.

## Administração

- importa Leads por CSV;
- atribui e reatribui Leads;
- vê indicadores, visitas, acessos e auditoria;
- exporta os quatro CSVs.

## Segurança

- senhas somente em hash;
- nenhum dado pessoal no QR;
- códigos imprevisíveis;
- CPF exibido conforme a seção 15;
- ações críticas auditadas; auditoria imutável.

---

# 25. Ordem de implementação

1. **Fundação:** estrutura do repositório, Docker Compose, PostgreSQL, Spring Boot, React, Flyway, health check, CI local (build e testes).
2. **Autenticação e usuários:** users, prospectors, sessão, CSRF, perfis, troca de senha, ADMIN inicial, limite de login, AuditService.
3. **Leads:** CRUD, carteira, status, atribuição, importação CSV, visibilidade de CPF.
4. **Visitas e acompanhantes:** criação, edição, remarcação, cancelamento, limites.
5. **Convites:** código, QR, reemissão, tela de convite e compartilhamento.
6. **Portaria:** validação, registro com lock, acompanhantes presentes, scanner, acessos recentes, job noturno.
7. **Chegadas e ficha:** perfil HOST, tela de chegadas, ficha e impressão.
8. **Dashboards.**
9. **Exportações e tela de auditoria.**
10. **E2E e revisão de testes.**
11. **Produção:** imagens, Nginx, HTTPS, variáveis, backup, logs.

Cada fase termina com build e testes passando.

---

# 26. Regras para o agente de IA

As regras operacionais estão em `CLAUDE.md` e são obrigatórias.

---

# 27. Princípio central

> Um Prospector consegue transformar um Lead em uma visita agendada com convite, a Portaria consegue validar essa visita de forma rápida, segura e rastreável, e o anfitrião recebe a ficha certa no momento da chegada?

Se sim, o núcleo do produto funciona. Todo o restante apoia esse fluxo.

---

# 28. Futuro, fora da V1

Integração com o sistema comercial, importação automática, WhatsApp, notificações e lembretes, recuperação de senha por e-mail, múltiplas unidades e portarias, atribuição de anfitriões, cadastro de acompanhante não previsto na Portaria, permissões granulares, aplicativo, BI, reconhecimento facial, acompanhamento da conversão.

**Fim da especificação V1.1.**
