# Registro de Decisões

Cada decisão que altera, esclarece ou completa a especificação fica registrada aqui. Novas decisões recebem o próximo número e nunca apagam as anteriores; se uma decisão for revista, criar uma nova que a substitua e marcar a antiga como "Substituída por D-XXX".

Formato: **Contexto**, **Decisão**, **Descartado**, **Impacto**.

---

## D-001 — Código único de convite

- **Contexto:** a 1.0 previa um token aleatório longo no QR e digitação manual como alternativa. Um token longo não é digitável.
- **Decisão:** um único código de 10 caracteres Crockford Base32 (cerca de 50 bits), usado tanto no QR quanto na digitação. Exibido como `ABCDE-FGHJK`.
- **Descartado:** token longo para o QR mais código curto separado para digitação; dobra a complexidade sem ganho real, porque a validação exige usuário GATE autenticado e tem limite de tentativas.
- **Impacto:** campo `invitations.code char(10) unique`.

## D-002 — QR contém só o código

- **Contexto:** a 1.0 sugeria uma URL no QR. Qualquer câmera de celular abriria essa URL.
- **Decisão:** o QR contém `RSV:` seguido do código. É lido apenas pelo scanner do app.
- **Descartado:** URL pública; exigiria uma página de destino e poderia expor informação.
- **Impacto:** nenhuma rota pública relacionada ao convite.

## D-003 — Código armazenado em claro

- **Contexto:** a 1.0 previa `code_hash`, mas também `GET /invitations/{id}/qr-code`. Com apenas o hash, o QR não pode ser reexibido.
- **Decisão:** armazenar o código em claro com índice único.
- **Descartado:** hash (impede reexibir e recompartilhar o convite); cifragem com chave de ambiente (complexidade sem ganho proporcional).
- **Impacto:** o código não é dado pessoal e só autoriza uma entrada numa data. Quem tiver acesso ao banco já tem acesso a todos os dados. O código não aparece em logs.

## D-004 — Validar e registrar em duas chamadas

- **Contexto:** a 1.0 pedia transação única, mas a tela da Portaria tinha o botão "Confirmar entrada".
- **Decisão:** `validate` é leitura (grava apenas negativas); `register` revalida tudo com `SELECT ... FOR UPDATE` e grava a entrada numa transação.
- **Descartado:** validar e registrar numa única chamada; impede o porteiro de conferir o documento e marcar acompanhantes antes de confirmar.
- **Impacto:** concorrência resolvida por lock pessimista; clique duplo resulta em `ALREADY_USED`.

## D-005 — Validade restrita à data agendada

- **Decisão:** o convite só é aceito na `scheduled_date`, no timezone `APP_TIMEZONE` (padrão `America/Sao_Paulo`). Antes da data: `WRONG_DATE`. Depois: `EXPIRED`.
- **Descartado:** janela de tolerância; nenhuma regra comercial a exige na V1.

## D-006 — Expiração calculada na validação

- **Decisão:** a validação compara a data na hora. Um job noturno apenas persiste `EXPIRED` e `NO_SHOW`.
- **Descartado:** depender só do job; uma falha do job liberaria convites vencidos.

## D-007 — Transições automáticas de status

- **Decisão:** as transições da seção 7.2 da SPEC são aplicadas pelo backend nos eventos correspondentes. O Prospector só altera manualmente `NEW → CONTACTED` e o descarte.
- **Impacto:** `NO_SHOW` e `VISITED` deixam de depender de ação manual.

## D-008 — Lead CANCELLED significa inativo

- **Contexto:** a RN01 da 1.0 falava em Lead inativo, mas não havia campo para isso.
- **Decisão:** o status `CANCELLED` cumpre esse papel. Prospector pode descartar; só ADMIN reativa.
- **Descartado:** campo `active` separado; dois conceitos para a mesma coisa.

## D-009 — Uma visita agendada por Lead; remarcação cria nova visita

- **Decisão:** índice único parcial em `visits(lead_id) where status = 'SCHEDULED'`. Remarcar cancela a visita atual e cria outra, na mesma transação, com novo código.
- **Descartado:** alterar a data da visita existente; o convite antigo, já compartilhado, continuaria válido para a data errada ou exigiria regras especiais.

## D-010 — Convite nasce com a visita; cancelamento pela visita; reemissão

- **Decisão:** não existe `POST /api/invitations` nem cancelamento isolado de convite. O convite é criado junto com a visita e cancelado junto com ela. `POST /invitations/{id}/reissue` troca o código quando necessário.
- **Descartado:** convite cancelável separadamente; deixaria visitas agendadas sem convite ativo.
- **Impacto:** o critério "cancelar convite" da 1.0 é atendido por cancelar visita e reemitir código.

## D-011 — Perfis

- **Decisão:** `ADMIN`, `PROSPECTOR`, `GATE`, `HOST` no código. Na interface: Administrador, Prospector, Portaria, Anfitrião.
- **Contexto:** a 1.0 usava `PORTARIA` e `GATE` para o mesmo perfil. `HOST` foi acrescentado com a funcionalidade de ficha (D-023).

## D-012 — CPF opcional, único quando informado

- **Decisão:** CPF opcional para Leads e acompanhantes, armazenado só com dígitos e validado. Único entre Leads quando presente (índice parcial). Visibilidade conforme seção 15 da SPEC; mascaramento no backend.
- **Descartado:** CPF obrigatório; listas de Leads frequentemente não o trazem.

## D-013 — Prospector da visita imutável; menos duplicação

- **Decisão:** `visits.prospector_id` recebe o responsável no momento do agendamento e não muda. `invitations` guarda só `visit_id`; `access_records` não guarda `lead_id`.
- **Descartado:** copiar Lead e Prospector em todas as tabelas; risco de inconsistência após reatribuição.
- **Impacto:** reatribuir um Lead não altera o histórico nem o crédito de visitas. O acesso do Prospector é verificado pelo dono atual do Lead.

## D-014 — Prospector sem campo `active` próprio

- **Decisão:** situação ativa fica apenas em `users.active`.
- **Descartado:** `prospectors.active`; duplicaria o estado.

## D-015 — Sessão no servidor em vez de JWT

- **Contexto:** a 1.0 permitia "JWT ou mecanismo equivalente".
- **Decisão:** Spring Security com Spring Session JDBC, cookie `HttpOnly` e CSRF. Frontend e API na mesma origem.
- **Descartado:** JWT com refresh token; exigiria tabela de refresh tokens, rotação e armazenamento seguro no frontend, e o logout não invalidaria de verdade sem lista de revogação.
- **Impacto:** `/api/auth/refresh` removido; migration das tabelas de sessão.

## D-016 — Sem recuperação de senha por e-mail

- **Decisão:** ADMIN redefine a senha, gerando uma temporária exibida uma única vez; o usuário é obrigado a trocá-la no próximo login.
- **Descartado:** fluxo por e-mail; exige SMTP e gestão de tokens de redefinição, não especificados.
- **Impacto:** `/forgot-password` e `/reset-password` removidos; `must_change_password` em `users`.

## D-017 — Limites de tentativa em memória

- **Decisão:** contador em memória para login (5 falhas por e-mail + IP em 15 minutos) e validação (30 por minuto por usuário GATE).
- **Descartado:** Redis ou biblioteca dedicada; desnecessário com instância única.
- **Impacto:** se houver mais de uma instância no futuro, revisar.

## D-018 — Importação tudo ou nada

- **Decisão:** qualquer erro no arquivo impede toda a importação; a resposta traz o relatório por linha.
- **Descartado:** importação parcial; gera dúvida sobre o que entrou e o que precisa ser reenviado.

## D-019 — Formato CSV

- **Decisão:** separador `;`, UTF-8 com BOM na exportação, datas `DD/MM/AAAA`.
- **Motivo:** abrir corretamente no Excel configurado em português.

## D-020 — Acompanhantes ligados à visita

- **Decisão:** tabela `visit_companions`. Nome, data de nascimento e parentesco obrigatórios; CPF opcional. Máximo configurável (`APP_MAX_COMPANIONS`, padrão 6). Editáveis enquanto a visita estiver `SCHEDULED`.
- **Descartado:** acompanhantes ligados ao Lead; quem vem em uma visita pode não vir em outra.

## D-021 — Presença dos acompanhantes na entrada

- **Decisão:** na liberação, a Portaria vê os acompanhantes todos marcados e desmarca quem não veio. A presença é gravada em `access_record_companions`.
- **Descartado:** cadastrar acompanhante novo na Portaria; aumenta o tempo de atendimento. Fica para o futuro.

## D-022 — Dados de acompanhantes e menores (LGPD)

- **Finalidade:** recepção na Portaria e apoio à apresentação da visita.
- **Decisão:** dados mínimos (nome, parentesco, nascimento, CPF opcional). A ficha mostra idade, não data de nascimento. Portaria e anfitrião não veem CPF.
- **Impacto:** exportação de acompanhantes restrita ao ADMIN e auditada.

## D-023 — Anfitrião

- **Contexto:** muitas vezes o anfitrião é o próprio Prospector; outras vezes é outra pessoa.
- **Decisão:** perfil `HOST` somente leitura, que vê todas as chegadas do dia e suas fichas. O Prospector vê as chegadas e fichas das visitas em que é o responsável da visita ou o dono atual do Lead. ADMIN vê todas.
- **Descartado:** atribuir anfitrião a cada visita e registrar resultado da apresentação; entra em gestão comercial, fora do escopo.

## D-024 — Observações para o anfitrião

- **Decisão:** campo `visits.host_notes`, preenchido pelo Prospector no agendamento, exibido na ficha. Separado de `leads.notes`, que é interno e não aparece na ficha.

## D-025 — Ficha impressa por CSS

- **Decisão:** versão de impressão com `@media print`, em uma página A4.
- **Descartado:** geração de PDF no backend; dependência extra sem necessidade.

## D-026 — Parâmetros por variáveis de ambiente

- **Decisão:** timezone, limite de acompanhantes, nome da portaria e tempo de sessão vêm do ambiente. Sem tela de configuração na V1.

## D-027 — Chaves primárias UUID

- **Decisão:** UUID em todas as tabelas.
- **Motivo:** identificadores aparecem em URLs; UUID evita enumeração.

## D-028 — Auditoria imutável

- **Decisão:** trigger no PostgreSQL impede UPDATE e DELETE em `audit_logs`. Registro por chamadas explícitas a `AuditService`, na mesma transação da ação.
- **Descartado:** AOP; menos explícito e mais difícil de testar.

## D-029 — Infraestrutura de produção

- **Decisão:** uma VPS com Docker Compose; Nginx serve o frontend e faz proxy de `/api` na mesma origem; HTTPS com Let's Encrypt; `pg_dump` diário com retenção de 14 dias.
- **Descartado:** frontend e API em domínios separados; exigiria CORS e cookies entre domínios.

## D-030 — Bibliotecas de frontend e testes

- **Decisão:** shadcn/ui (componentes acessíveis), Recharts (gráficos), `@zxing/browser` (scanner; a API nativa BarcodeDetector não funciona no iOS Safari), Vitest, Testing Library, Playwright.

## D-031 — Build do backend

- **Decisão:** Maven com wrapper (`mvnw`).

## D-032 — Primeiro administrador

- **Decisão:** criado na inicialização a partir de `APP_BOOTSTRAP_ADMIN_EMAIL` e `APP_BOOTSTRAP_ADMIN_PASSWORD`, apenas se não houver ADMIN, com troca obrigatória de senha.
- **Motivo:** evita seed com credencial fixa em produção.

## D-033 — Formato de erros

- **Decisão:** Problem Details (RFC 9457), suportado nativamente pelo Spring. Campo extra `code` com identificador estável (ex.: `LEAD_ALREADY_SCHEDULED`) para o frontend exibir mensagens em português.
