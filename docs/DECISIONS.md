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

## D-034 — Numeração das migrations pela ordem de criação

- **Contexto:** a §22.5 da SPEC numera as migrations de V1 a V8 por entidade, mas a ordem das fases (§25) cria `users`, `prospectors`, `audit_logs` e as tabelas de sessão (Fase 2) antes de `leads` (Fase 3). O Flyway rejeita, por padrão, uma versão menor criada depois de uma maior já aplicada.
- **Decisão:** as migrations são numeradas na ordem real em que são criadas. A §22.5 passa a ser apenas a lista do conteúdo esperado, não da numeração.
- **Descartado:** criar as oito migrations na Fase 1 (adiantaria o modelo de dados antes das fases correspondentes); ativar `outOfOrder` no Flyway (mascara erros de ordem).
- **Impacto:** a Fase 1 não tem migration. Cada fase cria as suas com o próximo número livre.

## D-035 — CI local e GitHub Actions

- **Decisão:** `scripts/verify.sh` é o CI local: `./mvnw verify` no backend; `npm ci`, lint, typecheck, testes e build no frontend. O workflow `.github/workflows/ci.yml` apenas executa esse script em todo push e pull request.
- **Descartado:** lógica de build duplicada no workflow; o script é a única definição do que é "build e testes passando".
- **Impacto:** requer Java 21, Node 22+ e Docker (Testcontainers) tanto na máquina local quanto no runner.

## D-036 — Spring Boot 4.1.x

- **Contexto:** a §22.3 da SPEC previa Spring Boot 3.x. A linha 3.5 está sem suporte OSS desde junho de 2026 e a 4.0 termina em dezembro de 2026.
- **Decisão:** Spring Boot 4.1.x, substituindo o "3.x" da §22.3. Versões gerenciadas pelo BOM 4.1.1 e confirmadas para a linha 4.1: Spring Framework 7.0, Hibernate 7.4, Flyway 12.4, Spring Session 4.1, Testcontainers 2.0, Jackson 3.1, JUnit 6.0. Fora do BOM: springdoc-openapi 3.1.x (compilado contra Boot 4.1).
- **Impacto:** starters modulares do Boot 4: `spring-boot-starter-webmvc` (no lugar de `-web`), `spring-boot-starter-flyway` (o Flyway não é mais autoconfigurado só com `flyway-core`) mais `flyway-database-postgresql`, `spring-boot-starter-session-jdbc` na Fase 2 e starters de teste por módulo (ex.: `spring-boot-starter-webmvc-test`). Testcontainers 2 usa os artefatos `testcontainers-postgresql` e `testcontainers-junit-jupiter`, com o pacote `org.testcontainers.postgresql`.

## D-037 — Perfil `dev` somente no `spring-boot:run`

- **Decisão:** o perfil `dev` é ativado apenas pela configuração do `spring-boot-maven-plugin`. O jar não tem perfil padrão e não sobe sem `SPRING_PROFILES_ACTIVE` explícito.
- **Descartado:** `spring.profiles.default=dev` no `application.yml`; um deploy sem perfil cairia em `dev`, com dados fictícios.

## D-038 — Docker Compose de desenvolvimento só com PostgreSQL

- **Decisão:** na Fase 1, o `docker-compose.yml` tem apenas o serviço `postgres`. Backend e frontend rodam localmente em desenvolvimento. Imagens e `docker-compose.prod.yml` ficam para a Fase 11.

## D-039 — Remarcação copia dados da visita

- **Contexto:** `POST /api/visits/{id}/reschedule` recebe só `{ scheduledDate }`, mas a remarcação cria uma visita nova (D-009).
- **Decisão:** a nova visita recebe cópia de `notes`, `host_notes` e dos acompanhantes, estes como novos registros em `visit_companions`.
- **Descartado:** exigir que o Prospector recadastre tudo; perda de dados sem motivo.

## D-040 — Estados de origem para agendar e descartar

- **Decisão:** agendar visita é permitido a partir de `NEW`, `CONTACTED` ou `VISITED`. Descartar o Lead é permitido a partir de qualquer estado exceto `CANCELLED`; se houver visita `SCHEDULED`, ela e o convite ativo são cancelados na mesma transação. Demais origens retornam 409.

## D-041 — Leitura e escrita do Prospector após reatribuição

- **Contexto:** D-013 verifica o acesso pelo dono atual do Lead; D-023 permite chegadas e fichas também ao responsável pela visita.
- **Decisão:** leitura (visita, convite, ficha, chegadas) é permitida ao Prospector responsável pela visita (`visits.prospector_id`) ou ao dono atual do Lead. Escrita (editar, remarcar, cancelar, reemitir) é permitida só ao dono atual do Lead ou ao ADMIN. Sem permissão, 404.
- **Impacto:** complementa D-013 e D-023.

## D-042 — Troca de role de usuário

- **Decisão:** `PUT /api/users/{id}` permite trocar a role apenas entre `ADMIN`, `GATE` e `HOST`. Troca para ou a partir de `PROSPECTOR` retorna 409.
- **Descartado:** criar ou remover o registro em `prospectors` na troca; afetaria carteira e histórico de visitas.

## D-043 — Prazo de edição da visita

- **Decisão:** vale a RN13: acompanhantes, `notes` e `host_notes` são editáveis enquanto a visita estiver `SCHEDULED`, inclusive no próprio dia até o registro da entrada. O "até o dia da visita" da §6.2 da SPEC é lido nesse sentido.

## D-044 — Metadata de ACCESS_DENIED

- **Decisão:** a auditoria `ACCESS_DENIED` grava em `metadata` apenas o `access_record_id`. O código tentado fica somente em `access_records.attempted_code`.
- **Motivo:** regra 5 do `CLAUDE.md`; nenhum código de convite fora do lugar necessário.

## D-045 — Respostas não especificadas

- **Decisão:**
  - limite de tentativas (login e validação) estourado: HTTP 429, Problem Details com `code` estável;
  - login de usuário inativo: o mesmo erro genérico de credenciais inválidas, com auditoria `LOGIN_FAILED`;
  - tela "Perfil" do Prospector: dados do próprio usuário somente leitura e troca de senha.

## D-046 — Senha inicial gerada pelo sistema

- **Decisão:** `POST /api/users` gera uma senha temporária (12 caracteres, `SecureRandom`, alfabeto sem caracteres ambíguos) e a devolve uma única vez, com `Cache-Control: no-store`, como no reset (D-016). O usuário nasce com `must_change_password = true`.
- **Descartado:** o ADMIN digitar a senha inicial; ele passaria a conhecer uma senha que pode ser reaproveitada.

## D-047 — Encerramento de sessões

- **Decisão:** desativação, troca de role e redefinição de senha pelo ADMIN encerram todas as sessões do usuário alvo (Spring Session JDBC, busca pelo principal). A troca da própria senha gera um novo id para a sessão atual e encerra as demais sessões do usuário.
- **Motivo:** a sessão guarda o perfil e a situação do momento do login; sem isso, um usuário desativado ou rebaixado continuaria operando até a sessão expirar.

## D-048 — Último ADMIN ativo

- **Decisão:** o último ADMIN ativo não pode ser desativado nem ter o perfil trocado; a tentativa retorna 409 `LAST_ADMIN`. Os ADMINs ativos são lidos com lock pessimista na mesma transação, para que duas alterações simultâneas não removam o último.

## D-049 — Regras de senha

- **Decisão:** mínimo de 10 caracteres (RN19), máximo de 72 bytes em UTF-8 (limite do BCrypt; a Spring Security 7 rejeita entradas maiores em vez de truncá-las) e a nova senha diferente da atual (`PASSWORD_UNCHANGED`).

## D-050 — Auditoria de falhas de login

- **Decisão:** `LOGIN_FAILED` grava `user_id` quando o e-mail pertence a um usuário e nulo caso contrário. O metadata tem apenas `reason` (`INVALID_CREDENTIALS`, `USER_INACTIVE` ou `RATE_LIMITED`). O e-mail digitado não é gravado. A resposta ao cliente é sempre a mesma (D-045).

## D-051 — Auditoria da edição de Prospector

- **Decisão:** `PUT /api/prospectors/{id}` é auditado como `USER_UPDATED` com `entity_type = PROSPECTOR` e `changedFields` no metadata, pois a §19 não tem ação própria para Prospector.

## D-052 — Criação do primeiro ADMIN

- **Decisão:** na inicialização, se já existe algum ADMIN, as variáveis `APP_BOOTSTRAP_ADMIN_*` são ignoradas. Se não existe, a aplicação cria o ADMIN com troca de senha obrigatória e auditoria `USER_CREATED` com `user_id` nulo. A aplicação **não sobe** quando não há ADMIN e falta alguma das variáveis, quando o e-mail é inválido, quando a senha viola a D-049 ou quando o e-mail já pertence a um usuário que não é ADMIN (ninguém é promovido em silêncio). A senha nunca aparece em log. Só o perfil `dev` tem valores padrão (fictícios).
- **Descartado:** subir com um alerta no log; o sistema ficaria inutilizável sem administrador.

## D-053 — Identidade do principal na sessão

- **Decisão:** o nome do principal guardado na sessão é o id do usuário, não o e-mail, para que o índice de sessões por principal continue válido depois de uma troca de e-mail.

## D-054 — CSRF para a SPA

- **Decisão:** `csrf.spa()` com `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` legível pelo JavaScript, `Path=/`, `SameSite=Lax`, `Secure` em produção) e header `X-XSRF-TOKEN`. Um filtro carrega o token em toda requisição, pois o `spa()` o deixa diferido e o cookie não seria emitido. O login também exige CSRF; o frontend obtém o cookie com o `GET /api/auth/me` inicial. Não existe endpoint `/csrf`. No login e no logout o token é trocado.
- **Nota:** o repositório por cookie é o padrão double-submit: o servidor compara header e cookie e não guarda estado. A rotação troca o cookie do navegador, e o token antigo deixa de corresponder a ele.

## D-055 — Troca de senha obrigatória por authorities

- **Decisão:** com `must_change_password`, a sessão recebe apenas a authority `PASSWORD_CHANGE_REQUIRED`, sem `ROLE_*`. As rotas `GET /api/auth/me`, `POST /api/auth/change-password` e `POST /api/auth/logout` exigem só autenticação; todo o resto de `/api/**` exige um perfil e responde 403 `PASSWORD_CHANGE_REQUIRED`. Rotas criadas nas próximas fases ficam bloqueadas automaticamente.
- **Descartado:** filtro dedicado; seria mais uma peça para manter em sincronia com as regras de rota.

## D-056 — springdoc-openapi só em desenvolvimento

- **Decisão:** springdoc-openapi 3.1.x incluído agora. `/v3/api-docs` e o Swagger UI ficam habilitados e liberados na segurança apenas no perfil `dev`; nos demais perfis não existem.

## D-057 — Usuário do banco sem ownership em produção

- **Decisão:** na Fase 11, as migrations rodam com um usuário do PostgreSQL dono do schema, e a aplicação conecta com outro usuário, sem ownership das tabelas e só com os privilégios de DML necessários. Assim a aplicação não consegue remover nem desabilitar o trigger de `audit_logs` (D-028). Não é opcional.
- **Impacto:** configuração separada de credenciais para o Flyway e para o datasource na Fase 11.

## D-058 — Dispatch de erro liberado no Spring Security

- **Contexto:** a Spring Security aplica autorização também ao dispatch interno de erro (`DispatcherType.ERROR`). Com a regra final `anyRequest().denyAll()` (D-055), o encaminhamento para `/error` seria negado, e um erro 500, ou um erro lançado por um filtro, chegaria ao cliente como 403.
- **Decisão:** `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` no `SecurityConfig`. A regra vale só para o dispatch interno de erro; nenhuma rota nova fica acessível por requisição direta.
- **Impacto:** o status original do erro é preservado. A resposta continua em Problem Details, sem detalhes internos (`GlobalExceptionHandler` devolve `INTERNAL_ERROR` genérico).

## D-059 — Prontidão da aplicação no healthcheck de produção (pendente, Fase 11)

- **Status:** pendente; implementar na Fase 11.
- **Contexto:** o `/actuator/health` responde `UP` assim que o servidor web sobe, antes de os `ApplicationRunner` terminarem, entre eles a criação do primeiro ADMIN (D-052). Na validação local de 2026-09-24, um login feito logo após o `UP` chegou 13 ms antes de o ADMIN existir e falhou. Os probes do Actuator foram desligados na Fase 1 porque, ligados, o `/actuator/health` passa a incluir `"groups":["liveness","readiness"]`, e a SPEC §11 define o endpoint público como "somente status".
- **Decisão (direção):** religar o readiness probe do Actuator e usá-lo no healthcheck do Docker. O estado de readiness só passa a `ACCEPTING_TRAFFIC` depois dos `ApplicationRunner`, incluindo o bootstrap do ADMIN. O `/actuator/health` público continua devolvendo apenas `{"status":"UP"}`, sem `groups` nem componentes. Se ligar os probes expuser `groups` no endpoint público, o readiness fica acessível só internamente: pela porta de management ou restrito na configuração do Nginx.
- **Impacto:** configuração do Actuator, healthcheck no `docker-compose.prod.yml` e testes que garantam readiness só depois do bootstrap e o endpoint público só com o status.

## D-060 — CPF na API conforme o perfil

- **Decisão:** a resposta de Lead é montada por `LeadResponse.of(lead, viewer)`. O ADMIN recebe o CPF completo e formatado (`123.456.789-01`); o PROSPECTOR recebe `***.456.789-**` (SPEC §15). O valor completo nunca é serializado para o PROSPECTOR, em nenhuma resposta, inclusive as de erro.
- **Impacto:** mensagens de erro e o `errors[]` de validação não repetem valores enviados; auditoria grava só `changedFields`.

## D-061 — Escrita do CPF do Lead

- **Decisão:** no `PUT`, `cpf` nulo ou ausente mantém o valor; `""` remove (só ADMIN). O PROSPECTOR só informa CPF quando o Lead não tem nenhum; não altera nem remove. Qualquer CPF enviado por ele para um Lead que já tem CPF retorna 409 `CPF_CHANGE_NOT_ALLOWED`, **mesmo que igual ao gravado**.
- **Motivo:** a máscara revela 6 dígitos e os 2 verificadores são calculáveis; se o valor igual fosse aceito como "sem mudança", a resposta viraria um oráculo para descobrir o CPF completo em até 1.000 tentativas.
- **Aceito:** o 409 `CPF_ALREADY_EXISTS` revela ao PROSPECTOR que o CPF existe em outro Lead; a resposta não traz nenhum dado desse Lead.

## D-062 — Criação de Lead já atribuído

- **Decisão:** `POST /api/leads` (ADMIN) aceita `prospectorId` opcional, que precisa existir e estar ativo. Auditoria `LEAD_CREATED` e, quando atribuído, `LEAD_ASSIGNED` com `fromProspectorId` nulo.

## D-063 — Mudança manual de status do Lead

- **Decisão:** `PATCH /api/leads/{id}/status` aceita `NEW → CONTACTED`, descarte (`NEW`, `CONTACTED`, `VISIT_SCHEDULED` ou `VISITED` → `CANCELLED`) e reativação (`CANCELLED → NEW`, só ADMIN). Qualquer outro par, inclusive o mesmo status e os destinos automáticos `VISIT_SCHEDULED` e `VISITED`, retorna 409 `INVALID_STATUS_TRANSITION`. Reativação pelo PROSPECTOR retorna 403 `ACCESS_DENIED`. O `PUT` não depende do status.
- **Pendente (Fase 4):** o descarte passa a cancelar a visita `SCHEDULED` e o convite ativo na mesma transação (D-040).

## D-064 — Atribuição em lote

- **Decisão:** `PATCH /api/leads/assign` com 1 a 500 ids distintos e `prospectorId` obrigatório; sem "desatribuir" na V1. Atômica: id inexistente (404 `LEAD_NOT_FOUND`), Prospector inexistente (404) ou inativo (409 `PROSPECTOR_INACTIVE`) não alteram nada. Leads lidos com lock pessimista. Leads que já pertencem ao destino são ignorados. Auditoria `LEAD_ASSIGNED` por Lead, com `fromProspectorId` e `toProspectorId`. Leads `CANCELLED` podem ser atribuídos.

## D-065 — Detalhes da importação CSV

- **Decisão:** UTF-8 com ou sem BOM (outro encoding: 400 `INVALID_ENCODING`); separador `;`; aspas RFC 4180; cabeçalho com as 7 colunas em qualquer ordem, sem extras nem repetidas (400 `INVALID_HEADER`); linhas totalmente vazias ignoradas; até 5.000 linhas de dados (400 `TOO_MANY_ROWS`) e 5 MB (413 `FILE_TOO_LARGE`). CPF aceito com ou sem pontuação. Prospector inativo é erro próprio (`PROSPECTOR_INACTIVE`). Observações até 2.000 caracteres.
- **Relatório:** 422 em Problem Details, `code = IMPORT_REJECTED`, `totalRows` e `errors[{line, column, code, message}]`, com a linha física do arquivo (cabeçalho = 1) e sem o conteúdo das células. Sucesso: 200 `{imported, totalRows}`.
- **Auditoria:** um único `LEAD_IMPORTED` com `count` e `assignedCount`; não há `LEAD_CREATED` por linha.
- **Parser:** leitor de CSV próprio (`CsvReader`), sem dependência nova.

## D-066 — Busca e filtros de Leads

- **Decisão:** `q` busca em nome, e-mail e telefone, sem diferenciar maiúsculas e sensível a acentos. Filtros `prospectorId`, `unassigned` e `cpf` (exato) valem só para o ADMIN; para o PROSPECTOR a lista é sempre a própria carteira e esses filtros são ignorados. Ordenação por nome.

## D-067 — Normalização de campos do Lead

- **Decisão:** e-mail gravado em minúsculas; data de nascimento não futura e a partir de 01/01/1900.

## D-068 — Injeção de fórmula na exportação CSV (pendente, Fase 9)

- **Status:** pendente; implementar na Fase 9.
- **Contexto:** a importação aceita qualquer texto em nome, observações e demais campos, inclusive valores começando com `=`, `+`, `-`, `@`, tabulação ou retorno de carro. Abertos no Excel, esses valores podem ser interpretados como fórmula.
- **Decisão:** toda exportação CSV neutraliza células que comecem com `=`, `+`, `-`, `@`, `\t` ou `\r` (prefixo `'` ou equivalente), com teste por caractere. A importação continua aceitando esses valores.

## D-069 — Logs de erro SQL do Hibernate desligados

- **Contexto:** o Hibernate 7 registra erros de SQL (loggers `org.hibernate.orm.jdbc.error` e `org.hibernate.engine.jdbc.spi.SqlExceptionHelper`) com os valores do comando e a chave violada, por exemplo `Key (cpf)=(...)`. Um teste de log flagrou o CPF na saída.
- **Decisão:** os dois loggers ficam em `OFF`. As exceções continuam sendo lançadas e tratadas: violação de constraint vira 409; erro inesperado é registrado pelo `GlobalExceptionHandler`.
- **Impacto:** diagnóstico de erro SQL passa a depender da exceção tratada, e não do log do Hibernate. O teste de log cobre a corrida que chega à constraint.

## D-070 — Risco residual: confirmação de CPF pelo Prospector (aceito na V1)

- **Status:** risco conhecido, aceito na V1.
- **Contexto:** a D-061 impede o Prospector de usar a edição do próprio Lead como oráculo do CPF mascarado. Ainda assim, a máscara `***.456.789-**` deixa só os 3 primeiros dígitos desconhecidos (1.000 candidatos, com os verificadores calculáveis). O Prospector pode informar cada candidato em Leads **da própria carteira sem CPF**: o candidato certo responde `409 CPF_ALREADY_EXISTS`, o que confirma o CPF completo do Lead A.
- **Por que é aceito:** o ataque é pouco prático. Cada tentativa errada é aceita, grava o CPF no Lead de teste e o consome, porque o Prospector não pode alterar nem remover um CPF (D-061); seriam necessários até ~1.000 Leads sem CPF na carteira. Cada tentativa gera `LEAD_UPDATED` na auditoria, e o volume anormal fica visível.
- **Mitigação futura, não implementada:** limitar a quantidade de CPFs que um Prospector pode informar por período (por exemplo, N por dia), com resposta 429 e auditoria ao atingir o limite.
