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
- **Complemento (Fase 11a, confirmado):**
  - **Passo de migração separado:** as migrations não rodam dentro da aplicação. O serviço `migrate` do `docker-compose.prod.yml` usa a mesma imagem do backend (`java -cp app.jar com.resort.platform.MigrateMain`), roda como `resort_owner`, aplica as migrations e depois o `db/prod/grants.sql`, e termina. O backend só sobe depois dele terminar com sucesso, com `spring.flyway.enabled=false` no perfil `prod` e só a senha de `resort_app`. Assim, quem tomar o processo da aplicação não tem a senha do dono para desligar o trigger.
  - **Papéis:** o `postgres/initdb/01-roles.sh` roda na criação do volume, como o superusuário da imagem, e cria três papéis com nomes fixos e senhas do `.env`:
    - `resort_owner`: dono do banco e do schema `public`;
    - `resort_app`: só DML;
    - `resort_backup`: `pg_read_all_data`, para o `pg_dump`.

    `PUBLIC` perde o acesso ao banco e ao schema.
  - **Permissões de `resort_app`**, no `grants.sql`, idempotente e reaplicado a cada migração:
    - `SELECT`, `INSERT`, `UPDATE` e `DELETE` nas tabelas;
    - privilégios padrão, para que as tabelas de migrations futuras já nasçam com esse DML;
    - `REVOKE UPDATE, DELETE, TRUNCATE` em `audit_logs`, além do trigger;
    - nenhum acesso ao `flyway_schema_history`.
  - **Testes:** `DatabaseRolesTest` e `ProductionStartupTest`, com o mesmo script de papéis e o mesmo `MigrateMain`. `resort_app` recebe "permission denied" (SQLSTATE 42501) em todas estas tentativas:
    - desligar, trocar para réplica ou apagar o trigger;
    - redefinir a função do trigger;
    - `session_replication_role`;
    - `UPDATE`, `DELETE` e `TRUNCATE` em `audit_logs`;
    - criar, alterar ou apagar tabelas, índices, views e funções.

    Os testes também provam que migrar de novo não muda nada, que `resort_backup` só lê e que a aplicação sobe e faz login como `resort_app`. Três mutações foram detectadas: `resort_app` como membro de `resort_owner`, sem o `REVOKE` e sem os privilégios padrão.
- **Complemento (Fase 11a, PR 2, pedido na revisão): `pg_hba.conf`.** O `postgres/pg-entrypoint.sh` gera o arquivo a cada subida, fora do volume de dados (`-c hba_file=...`), a partir de `INTERNAL_SUBNET`, e chama o entrypoint oficial.

  | Regra | Quem | Por onde | Resultado |
  |---|---|---|---|
  | `local all postgres peer` | superusuário | socket local, dentro do container | aceito |
  | `local all all reject` | qualquer outro papel | socket local | recusado |
  | `host resort resort_owner,resort_app,resort_backup <sub-rede interna> scram-sha-256` | os três papéis | sub-rede interna do compose, com senha | aceito |
  | `host all all 0.0.0.0/0 reject` e `::/0 reject` | qualquer outra combinação | rede | recusado |

  O superusuário pela rede é recusado mesmo com a senha certa. O `prod-check.sh` confere que:
  - um container na própria rede interna, com a senha do superusuário, recebe `pg_hba.conf rejects connection`;
  - o superusuário entra pelo socket local;
  - `resort_app` pelo socket local é recusado;
  - as conexões da aplicação são de `resort_app`, vindas do IP do container do backend.

  A mutação que liberou o superusuário pela rede foi detectada. Os testes do backend com Testcontainers continuam sem o `pg_hba`, porque conectam pela porta mapeada do host.

## D-058 — Dispatch de erro liberado no Spring Security

- **Contexto:** a Spring Security aplica autorização também ao dispatch interno de erro (`DispatcherType.ERROR`). Com a regra final `anyRequest().denyAll()` (D-055), o encaminhamento para `/error` seria negado, e um erro 500, ou um erro lançado por um filtro, chegaria ao cliente como 403.
- **Decisão:** `dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()` no `SecurityConfig`. A regra vale só para o dispatch interno de erro; nenhuma rota nova fica acessível por requisição direta.
- **Impacto:** o status original do erro é preservado. A resposta continua em Problem Details, sem detalhes internos (`GlobalExceptionHandler` devolve `INTERNAL_ERROR` genérico).

## D-059 — Prontidão da aplicação no healthcheck de produção

- **Status:** implementada na Fase 11a (PR 1), na direção abaixo.
- **Contexto:** o `/actuator/health` responde `UP` assim que o servidor web sobe, antes de os `ApplicationRunner` terminarem, entre eles a criação do primeiro ADMIN (D-052). Na validação local de 2026-09-24, um login feito logo após o `UP` chegou 13 ms antes de o ADMIN existir e falhou. Os probes do Actuator foram desligados na Fase 1 porque, ligados, o `/actuator/health` passa a incluir `"groups":["liveness","readiness"]`, e a SPEC §11 define o endpoint público como "somente status".
- **Decisão (direção):** religar o readiness probe do Actuator e usá-lo no healthcheck do Docker. O estado de readiness só passa a `ACCEPTING_TRAFFIC` depois dos `ApplicationRunner`, incluindo o bootstrap do ADMIN. O `/actuator/health` público continua devolvendo apenas `{"status":"UP"}`, sem `groups` nem componentes. Se ligar os probes expuser `groups` no endpoint público, o readiness fica acessível só internamente: pela porta de management ou restrito na configuração do Nginx.
- **Impacto:** configuração do Actuator, healthcheck no `docker-compose.prod.yml` e testes que garantam readiness só depois do bootstrap e o endpoint público só com o status.
- **Implementação (Fase 11a):**
  - No perfil `prod`, os probes ficam ligados e o management vai para a porta interna `MANAGEMENT_PORT` (8081), que não é publicada.
  - O healthcheck do Docker consulta `http://127.0.0.1:8081/actuator/health/readiness`. O Nginx encaminha o `/actuator/health` público para esse readiness, que devolve só `{"status":"UP"}`. Qualquer outro `/actuator/*` e o `/v3/*` dão 404 no Nginx, e a porta pública do backend não tem actuator.
  - O Spring Boot só publica `ACCEPTING_TRAFFIC` no `ApplicationReadyEvent`, depois de todos os `ApplicationRunner`.
  - **Testes:** o `ProductionStartupTest` sobe o perfil `prod` e confere que:
    - durante os runners, o readiness responde 503;
    - no momento de `ACCEPTING_TRAFFIC`, o ADMIN inicial já existe;
    - o readiness responde exatamente `{"status":"UP"}`;
    - a porta pública não tem actuator.

    O `scripts/prod-check.sh` faz login logo depois de a pilha ficar saudável e confere o health público pelo Nginx.

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

## D-068 — Injeção de fórmula na exportação CSV

- **Status:** implementada na Fase 9 (`CsvWriter`, testes em `CsvWriterTest`, um por caractere; detalhes na D-101).
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

## D-071 — RN06 (convite na mesma transação) a partir da Fase 5

- **Contexto:** a RN06 exige o convite criado na mesma transação da visita, mas convites são da Fase 5.
- **Decisão:** na Fase 4 a visita é criada sem convite. A Fase 5 insere o convite dentro das mesmas transações de criação e remarcação, e o cancelamento do convite dentro das transações de cancelamento, remarcação e descarte do Lead. A Fase 5 terá teste de que toda visita `SCHEDULED` tem exatamente um convite `ACTIVE` e de que o rollback da visita desfaz o convite. Não há criação retroativa de convites: bancos de desenvolvimento da Fase 4 são recriados (`docker compose down -v`).
- **Descartado:** tabela de convites parcial na Fase 4; anteciparia código, QR e reemissão sem os testes e telas da Fase 5.
- **Motivo:** o sistema só vai para produção na Fase 11; nenhum dado real fica com visita sem convite.

## D-072 — Acesso a visitas

- **Decisão:** segue a D-041. Leitura: ADMIN, ou PROSPECTOR responsável pela visita (`visits.prospector_id`) ou dono atual do Lead; a lista do PROSPECTOR contém exatamente essas visitas. Escrita (editar, remarcar, cancelar): ADMIN ou dono atual do Lead. Sem permissão, 404 `VISIT_NOT_FOUND` com o mesmo corpo de um id inexistente, **inclusive para quem pode ler e tenta escrever** (confirmado na aprovação da Fase 4).

## D-073 — Regras de criação de visita

- **Decisão:** a criação trava a linha do Lead (`SELECT … FOR UPDATE`). Ordem das verificações: sem acesso de escrita, 404 `LEAD_NOT_FOUND`; Lead `CANCELLED`, 409 `LEAD_INACTIVE` (RN01); sem Prospector, 409 `LEAD_NOT_ASSIGNED` (RN03); dono inativo, 409 `PROSPECTOR_INACTIVE` (o ADMIN reatribui antes); visita já agendada, 409 `VISIT_ALREADY_SCHEDULED` (RN04). `visits.prospector_id` recebe o dono atual do Lead. O índice parcial `visits_lead_scheduled_uk` é o último seguro e também vira 409 `VISIT_ALREADY_SCHEDULED`.
- **Ordem de lock:** toda escrita de visita e toda mudança de status do Lead trava o Lead **antes** de ler o estado da visita. Assim quem esperou o lock lê o estado já confirmado pela transação concorrente (evita, por exemplo, um cancelamento que desfaria um descarte).

## D-074 — Data da visita

- **Decisão:** "hoje" é sempre calculado por `BusinessCalendar` em `APP_TIMEZONE`, com `Clock` injetável (substituído nos testes). A data da visita, na criação e na remarcação, vai de hoje até **12 meses a partir de hoje, inclusive** (`today.plusMonths(12)`). Antes de hoje: 400 `SCHEDULED_DATE_IN_PAST`; depois do limite: 400 `SCHEDULED_DATE_TOO_FAR`.
- **Motivo do limite:** uma data digitada errada travaria o Lead, já que só existe uma visita agendada por vez (confirmado na aprovação da Fase 4).

## D-075 — Acompanhantes

- **Decisão:** no máximo `APP_MAX_COMPANIONS` (400 `TOO_MANY_COMPANIONS`, com o limite na mensagem; o frontend não fixa o número). Nome, nascimento (não futuro, a partir de 1900) e parentesco obrigatórios; CPF opcional, validado, não único. Editáveis só com a visita `SCHEDULED` (409 `VISIT_NOT_EDITABLE`), inclusive depois da data enquanto não houver entrada (D-043).
- **Lista no `PUT`:** substitui a atual casando por `id`: com `id` desta visita, atualiza e mantém o id (a Fase 6 registra presença por ele); sem `id`, cria; ausente, remove; `id` de outra visita, 400 `COMPANION_NOT_FOUND`.
- **CPF:** mascarado para quem não é ADMIN (D-060); `null` mantém, `""` remove (só ADMIN); qualquer CPF enviado pelo PROSPECTOR para acompanhante que já tem CPF, mesmo igual, retorna 409 `CPF_CHANGE_NOT_ALLOWED` (mesma proteção da D-061). Como o CPF de acompanhante não é único, não há o risco residual da D-070.
- **Auditoria:** `VISIT_UPDATED` com `changedFields` e contagens; nunca nome nem CPF.

## D-076 — Remarcação

- **Decisão:** só visita `SCHEDULED` (409 `INVALID_VISIT_TRANSITION`); a nova data segue a D-074 e não pode ser igual à atual (400 `SAME_DATE`; troca de código é papel da reemissão, Fase 5). Na mesma transação: a visita antiga vira `CANCELLED` com `cancelled_at` e é gravada antes da nova (o índice parcial exige); a nova nasce `SCHEDULED` com cópias de `notes`, `host_notes` e dos acompanhantes como registros novos (D-039), e com o dono atual do Lead como responsável (RN03). O Lead continua `VISIT_SCHEDULED`. Auditoria: `VISIT_RESCHEDULED` na antiga (`newVisitId`) e `VISIT_CREATED` na nova (`rescheduledFrom`).

## D-077 — Cancelamento de visita e descarte do Lead

- **Decisão:** cancelar leva a visita a `CANCELLED` (com `cancelled_at`) e o Lead de `VISIT_SCHEDULED` a `CONTACTED`, com `VISIT_CANCELLED` e `LEAD_STATUS_CHANGED` (`cause: VISIT_CANCELLED`). O descarte do Lead cancela a visita `SCHEDULED` na mesma transação, com `VISIT_CANCELLED` (`reason: LEAD_DISCARDED`); visitas passadas não mudam. Reativar o Lead não restaura visitas. Concorrência entre descarte, agendamento e cancelamento é resolvida pela ordem de lock da D-073 e coberta por testes em HTTP real.

## D-078 — `canEdit` e ficha da visita

- **Decisão:** a resposta da visita traz `canEdit`, calculado no backend para quem vê (escrita permitida e visita `SCHEDULED`). Serve só para a interface; o backend valida toda escrita. A ficha (`GET /api/visits/{id}/sheet`) fica para a Fase 7.
- **`lead.accessible` (adicionado no frontend da Fase 4):** a resposta da visita traz também `lead.accessible`, calculado no backend com a mesma regra de carteira do `GET /api/leads/{id}` (D-041: ADMIN ou PROSPECTOR dono atual), num único método (`LeadService.isInWallet`) usado pelas duas rotas. O responsável antigo, que ainda lê a visita (D-072), recebe `false` e a interface mostra só o nome do Lead, sem link. É independente de `canEdit`: o dono atual de uma visita cancelada recebe `canEdit: false` e `lead.accessible: true`. O nome do Lead já era exibido a quem lê a visita; nenhum dado novo é exposto.

## D-079 — Escopo "Histórico" na lista de visitas

- **Contexto:** a Agenda do PROSPECTOR (§16.1) lista as visitas `SCHEDULED` de hoje em diante (`status=SCHEDULED&from=hoje`); o Histórico lista as demais. Os filtros da lista (um status e um intervalo de datas) não expressam essa exclusão numa consulta paginada.
- **Decisão:** `GET /api/visits?scope=history` exclui as visitas `SCHEDULED` com data a partir de hoje, com "hoje" calculado pelo `BusinessCalendar` em `APP_TIMEZONE` (D-074). Combina com os demais filtros e com `order`; o Histórico usa `order=desc`. Outro valor de `scope` é 400 `VALIDATION_ERROR`. Entram no Histórico as visitas canceladas com data futura (por exemplo, a original de uma remarcação) e as `SCHEDULED` com data passada ainda sem entrada (D-043). Aprovado durante o frontend da Fase 4.
- **Descartado:** filtrar só por data no frontend, que deixaria de fora as canceladas com data futura e as encerradas no próprio dia.

## D-080 — "Hoje" nos formulários de data do frontend

- **Contexto:** os campos de data de agendamento e remarcação limitam a escolha entre hoje e hoje + 12 meses (D-074), mas o frontend não recebe `APP_TIMEZONE`.
- **Decisão:** o frontend calcula "hoje" em `America/Sao_Paulo`, o valor padrão de `APP_TIMEZONE`, e soma 12 meses como o `LocalDate.plusMonths` do backend (29/02 vira 28/02). Esses limites só orientam a digitação; o backend continua a autoridade, e os erros `SCHEDULED_DATE_IN_PAST` e `SCHEDULED_DATE_TOO_FAR` aparecem em português. Se a operação mudar de fuso, a constante do frontend acompanha a mudança.
- **Descartado:** endpoint de configuração só para expor o fuso; nova dependência entre telas e backend sem ganho de segurança.

## D-081 — Convite dentro das transações da visita

- **Decisão:** cumpre a D-071 a partir da Fase 5. `InvitationService.issue` e `cancelActive` usam `@Transactional(propagation = MANDATORY)` e são chamados pelo `VisitService` depois do lock no Lead: criação (`issue`), remarcação (`cancelActive` da antiga, antes do flush, e `issue` da nova), cancelamento e descarte do Lead (`cancelActive`, com `reason` `VISIT_RESCHEDULED`, `VISIT_CANCELLED` ou `LEAD_DISCARDED`). Qualquer falha desfaz visita e convite juntos. Invariante testado: toda visita `SCHEDULED` tem exatamente um convite `ACTIVE`, e nenhuma outra visita tem convite `ACTIVE`.
- **Visitas anteriores à Fase 5 (confirmado na aprovação):** não há criação retroativa. O sistema as tolera: `invitation` vem `null`, cancelar e descartar funcionam (`cancelActive` sem convite ativo não faz nada) e remarcar gera o convite da nova visita. O `docker compose down -v` continua sendo a orientação para bancos de desenvolvimento.
- **Descartado:** trigger `DEFERRABLE` impondo o convite no banco; traria regra de negócio para o banco e travaria as visitas antigas de desenvolvimento.

## D-082 — Geração do código do convite

- **Decisão:** `InvitationCodeGenerator` monta 10 caracteres de `0123456789ABCDEFGHJKMNPQRSTVWXYZ` com um `RandomGenerator`, que em produção é um `SecureRandom` (bean `CommonConfig.secureRandom`; nos testes, um `ScriptedRandom` que permite provocar colisão). Até 5 tentativas, cada uma checando se o código já existe em qualquer convite, inclusive cancelado: códigos nunca são reaproveitados. Esgotadas as tentativas, 500 genérico. O índice único `invitations_code_uk` é o último seguro e vira 409 `INVITATION_CODE_CONFLICT`, sem repassar a mensagem do banco, que traz o código. A tabela também tem `CHECK` do alfabeto.

## D-083 — QR do convite

- **Decisão:** `GET /api/invitations/{id}/qr-code` devolve PNG de 512 px, correção de erro M, margem 2, com `Cache-Control: no-store`. Conteúdo exatamente `RSV:` + código (RN08). Só para convite `ACTIVE`; nos demais status, 409 `INVITATION_NOT_ACTIVE` (confirmado na aprovação), para não circular código morto.

## D-084 — Reemissão do convite

- **Decisão:** `POST /api/invitations/{id}/reissue` trava o Lead antes de ler o convite (mesma ordem da D-073). Sem escrita (D-041), inclusive para o responsável antigo que só lê, 404 `INVITATION_NOT_FOUND` com o corpo de um id inexistente. Convite fora de `ACTIVE`, ou visita fora de `SCHEDULED`, 409 `INVITATION_NOT_ACTIVE`. O atual vira `CANCELLED` e é gravado antes do insert do novo (índice parcial); o novo tem código novo e o mesmo `expires_at`. Auditoria: `INVITATION_REISSUED` no antigo (`newInvitationId`) e `INVITATION_CREATED` no novo (`visitId`, `reissuedFrom`). Visita e acompanhantes não mudam.

## D-085 — `expires_at` do convite

- **Decisão:** `BusinessCalendar.endOfDay(data)` = primeiro instante do dia seguinte à visita em `APP_TIMEZONE`, calculado com as regras do fuso (correto em horário de verão). É limite exclusivo: a partir dele o convite está expirado (confirmado na aprovação). Na remarcação, acompanha a nova data; na reemissão, é mantido. A portaria continua comparando a data na hora (D-006).

## D-086 — Convite na resposta da visita e da API de convites

- **Decisão:** `VisitResponse.invitation` traz só `{id, status}` do convite atual (o `ACTIVE` ou, sem ele, o mais recente); o código aparece apenas nas rotas de convite, para quem pode ler a visita (D-041). `InvitationResponse` traz `code`, `formattedCode`, status e datas, a visita (`scheduledDate`, `status`, `companionsCount`, `canEdit`), `lead {id, name, accessible}`, `prospector` e `canReissue` (escrita, convite `ACTIVE` e visita `SCHEDULED`). Nenhum CPF. Lista `GET /api/invitations` com `status`, `visitId`, `leadId`, `prospectorId` (só ADMIN) e `order`, na ordem da data da visita. Auditoria de convite leva só ids e motivo, nunca o código.

## D-087 — Imagem de compartilhamento do convite e nome do Resort

- **Decisão:** a imagem é montada no frontend em canvas (§13), com 1080 × 1440 px: nome do Resort, "Convite de visita", nome do Lead, "Visita em DD/MM/AAAA", o QR vindo da API, o código `ABCDE-FGHJK` e a instrução "Apresente este código na portaria" (pedido na aprovação da Fase 5). Nada de CPF, telefone, e-mail, acompanhantes ou Prospector. O arquivo se chama `convite-ABCDE-FGHJK.png`. "Compartilhar" usa a Web Share API com arquivo quando `navigator.canShare({ files })` aceita; senão, baixa. "Baixar" sempre baixa. Cancelar o compartilhamento não é erro.
- **Nome do Resort (revisto na Fase 11a, a pedido):** a mesma imagem precisa servir para qualquer cliente e para a demonstração, então o nome não entra no build.
  - O `index.html` traz `<meta name="resort-name" content="" />`.
  - O `nginx/40-resort-name.sh` roda a cada subida do container. Ele gera o `index.html` servido a partir de um modelo, com `RESORT_NAME` escapada para HTML (`&`, `<`, `>`, `"` e `'` viram entidades; quebras de linha são removidas).
  - O frontend lê a meta tag em `frontend/src/lib/resort.ts`, com "Resort" como padrão quando a meta está vazia ou ausente.
  - A meta tag substitui um script inline porque a CSP proíbe script inline.
  - `VITE_RESORT_NAME` deixou de existir.
  - O nome real fica só no `.env.prod` da VPS, nunca num arquivo do repositório.
  - **Testes:**
    - `resort.test.ts`: sem meta, meta vazia, espaços e um valor escapado com aspas e `<script>`, que volta como texto sem criar elemento;
    - `prod-check.sh`: um nome com aspas, `<b>` e `&` chega intacto ao `index.html` servido, sem criar elemento; sem a variável, a meta fica vazia. A mutação sem o escape das aspas foi detectada.
- **Descartado:** endpoint de configuração só para o nome (nenhum ganho para um texto fixo da operação); nome real numa constante ou num `.env` versionado (o repositório é público); `VITE_RESORT_NAME` no build (a imagem ficaria presa a um cliente); script inline com o nome (a CSP proíbe).
- **Navegação (confirmado na aprovação):** depois de agendar, remarcar ou reemitir, a tela abre o convite novo, porque o código novo precisa ser compartilhado.

## D-088 — Respostas da validação e do registro na Portaria

- **Decisão:** `POST /api/access/validate` e `POST /api/access/register` respondem 200 com `result` (`AUTHORIZED` ou `DENIED`); a negativa é resultado de negócio, não erro (confirmado na aprovação da Fase 6). Liberado na validação: `invitationId`, `leadName`, `scheduledDate`, `prospectorName` e `companions [{id, name, relationship}]`, só o que a §15 permite ao GATE. Liberado no registro: `invitationId`, `leadName`, `accessRecordId`, `entryAt`. Negado: `denialReason` e, só em `WRONG_DATE` e `EXPIRED`, `scheduledDate` (confirmado). Código em branco ou com mais de 64 caracteres: 400 `VALIDATION_ERROR`, sem registro, porque não é tentativa de acesso. `invitationId` inexistente no registro: 404 `INVITATION_NOT_FOUND`.
- **Normalização (§12.1):** maiúsculas, prefixo `RSV:` só no início, sem espaços e hífens, O→0 e I/L→1; fora do formato Crockford de 10 caracteres, `INVALID_CODE`. `attempted_code` guarda o valor normalizado, cortado em 20 caracteres.

## D-089 — Registro da entrada

- **Decisão:** o registro trava o Lead e só depois o convite (`SELECT … FOR UPDATE` da §12.3), a mesma ordem da D-073 usada por cancelamento, remarcação, reemissão, descarte e job; a ordem inversa gera deadlock com essas operações (verificado por mutação: 20 de 25 execuções concorrentes falham). Revalida as verificações 2 a 5; negativa grava `AccessRecord DENIED` e `ACCESS_DENIED` e responde 200 com o motivo (clique duplo → `ALREADY_USED`). `presentCompanionIds` precisa conter só acompanhantes da visita, sem repetição (400 `COMPANION_NOT_FOUND` ou `VALIDATION_ERROR`); lista vazia é aceita. Índice único `access_records_invitation_authorized_uk` é o último seguro contra entrada dupla. Auditoria `ACCESS_VALIDATED` com `{access_record_id, visit_id, companions_present}` e `LEAD_STATUS_CHANGED` com `cause: ACCESS_REGISTERED` (confirmado). `created_at` e `entry_at` vêm do `Clock` da aplicação.

## D-090 — Limite de validações

- **Decisão:** `ValidationRateLimiter` em memória, janela deslizante de 60 s por usuário GATE, 30 chamadas (D-017). Acima disso, 429 `TOO_MANY_VALIDATIONS`, sem `AccessRecord` nem auditoria. Chamadas recusadas não contam. O registro não é limitado, porque só acontece depois de uma validação contada.

## D-091 — Job noturno de expiração

- **Decisão:** `InvitationExpiryJob` com `@Scheduled(cron = "0 15 0 * * *", zone = "${app.timezone}")`. Busca, sem lock, os convites `ACTIVE` com `expires_at` no passado, inclusive de noites em que não rodou, e processa cada um numa transação própria (`InvitationExpiryService.expire`): trava o Lead, relê o convite e, se ainda estiver `ACTIVE` e vencido, o convite vira `EXPIRED`, a visita `SCHEDULED` vira `NO_SHOW` e o Lead `VISIT_SCHEDULED` volta a `CONTACTED`, com `INVITATION_EXPIRED`, `VISIT_NO_SHOW` e `LEAD_STATUS_CHANGED` (`cause: VISIT_NO_SHOW`) e `user_id` nulo. Idempotente. Falha num item não impede os demais; o log leva só contagens e ids. O agendamento depende de `app.jobs.enabled` (padrão `true`, `false` no perfil test, em que o job é chamado diretamente). Visitas legadas sem convite (D-071) não são tocadas (confirmado).

## D-092 — Acessos recentes

- **Decisão:** `GET /api/access/recent` (GATE e ADMIN) devolve os registros de hoje em `APP_TIMEZONE`, do mais recente para o mais antigo, no máximo 50: `id`, `createdAt`, `result`, `denialReason`, `leadName` (quando há convite), `gate` e `validatedBy`. Nunca o código tentado nem CPF. A tela "Acessos" do ADMIN usa a mesma lista nesta fase (confirmado).

## D-093 — Scanner da Portaria e contexto seguro

- **Decisão:** o scanner usa `BrowserQRCodeReader` do `@zxing/browser` (D-030), versão fixada em `0.2.1` no `package.json`, com a câmera traseira (`facingMode: environment`). O texto lido vai como está para `POST /api/access/validate`; quem normaliza é o backend (§12.1). A câmera para logo após a primeira leitura e ao sair da tela. Cada abertura cria o seu `<video>`: ao parar, o zxing limpa o vídeo que recebeu, e com um elemento compartilhado a dupla montagem do StrictMode apagava a imagem da câmera (achado na verificação manual). O navegador só libera a câmera em contexto seguro (`window.isSecureContext`): sem ele, sem permissão ou sem câmera, a tela avisa em português e mantém a digitação, e as próximas validações voltam à digitação com o campo limpo em vez de pedir a câmera de novo.
- **Desenvolvimento:** `http://localhost` é contexto seguro, então a câmera funciona no computador. No celular, o acesso por `http://<ip-da-máquina>:5173` não é seguro; usar o redirecionamento de porta por USB do Chrome (`chrome://inspect` → *Port forwarding* `5173 → localhost:5173`) e abrir `http://localhost:5173` no celular (Problemas comuns do CLAUDE.md).
- **Pendências da Fase 11:** HTTPS com HSTS; `Permissions-Policy: camera=(self)`; CSP que não bloqueie o vídeo da câmera (o `<video>` recebe um `MediaStream` via `srcObject`, sem URL externa; conferir `media-src` e `worker-src` se a política os restringir); teste real num celular com HTTPS.

## D-094 — Tela da Portaria e lista de acessos

- **Decisão:** `/portaria` só para GATE, com [ESCANEAR QR CODE] e [DIGITAR CÓDIGO] (§16.5). A máscara põe em maiúsculas, troca O por 0 e I/L por 1, remove o que está fora do alfabeto Crockford, limita a 10, exibe `XXXXX-XXXXX` e envia sem hífen; "VALIDAR" só habilita com 10 caracteres. Liberado mostra nome, data, Prospector e acompanhantes todos marcados; [CONFIRMAR ENTRADA] envia só os marcados. Além do que a §16.5 desenha, um "Cancelar" discreto volta ao scanner sem registrar, para o porteiro que validou o convite errado. Confirmar e [NOVA VALIDAÇÃO] voltam ao scanner (ou à digitação, sem câmera). Negativa no registro (clique duplo) cai na mesma tela de negado.
- **Erro na validação (429, rede):** a mensagem aparece em português; se veio da câmera, a tela volta ao início em vez de reabrir o scanner, que leria o mesmo QR e repetiria o erro; se veio da digitação, o código digitado fica no campo.
- **Acessos:** rota própria `/acessos` para GATE ("Acessos Recentes") e ADMIN ("Acessos"), fora da proteção do `/portaria`, porque o ADMIN consulta acessos mas não valida convites (§4.5). Mostra hora no fuso da operação, resultado, motivo, Lead, portaria e quem validou, na ordem da API (D-092).

## D-095 — Chegadas do dia

- **Decisão:** `GET /api/arrivals?date=YYYY-MM-DD` (padrão: hoje) devolve `{ date, arrivals: [{ visitId, entryAt, leadName, companionsPresent, prospectorName }] }`, com as entradas liberadas cujo `entry_at` cai no dia em `APP_TIMEZONE` (`BusinessCalendar.startOfDay`/`endOfDay`), da mais recente para a mais antiga. ADMIN e HOST veem todas; o PROSPECTOR, as visitas em que é o responsável ou o dono atual do Lead (D-041), em qualquer data (confirmado). O HOST só consulta hoje: outra data é 403 `ARRIVALS_DATE_NOT_ALLOWED` (confirmado). Data malformada segue a convenção dos demais parâmetros: 400 `BAD_REQUEST`. Índice parcial `access_records_entry_at_ix` (V9, confirmado). Leitura sem auditoria.
- **Polling (acréscimo na aprovação):** a tela consulta a cada 30 s (§16.6) e, com isso, mantém a sessão ativa enquanto estiver aberta. É aceito por ser uma tela de recepção; o encerramento de sessão pelo ADMIN (D-047) continua valendo, e o pedido seguinte recebe 401. No frontend, 401 leva ao login e 403 `PASSWORD_CHANGE_REQUIRED` à troca de senha, como nas outras telas; falha de rede ou 5xx mantém a lista anterior com o aviso "Não foi possível atualizar. Nova tentativa em instantes.", que some no próximo sucesso. O ADMIN só faz polling com a data de hoje (confirmado).

## D-096 — Ficha da visita

- **Decisão:** `GET /api/visits/{id}/sheet` devolve `visitId`, `scheduledDate`, `entryAt`, `lead { name, age, phone }`, `prospectorName`, `presentCompanions [{ name, relationship, age }]`, `absentCompanions [{ name, relationship }]` e `hostNotes`, com os acompanhantes na ordem da visita (§15, §16.6, D-022, D-024). Os DTOs não têm campo para CPF, e-mail, data de nascimento, `leads.notes` nem `visits.notes`. Idades na data da visita, que é também o dia da entrada; Lead sem data de nascimento tem `age: null` (confirmado).
- **Acesso:** ADMIN, qualquer visita; PROSPECTOR, a leitura da D-041; ambos em qualquer data. Visita sem entrada para quem a lê: 409 `VISIT_NOT_ARRIVED`. HOST: só a ficha de uma chegada de hoje em `APP_TIMEZONE`; qualquer outro caso, inclusive visita sem entrada, é o 404 `VISIT_NOT_FOUND` de um id inexistente (confirmado). A rota fica no módulo `arrivals`, liberada ao HOST só nela (`GET /api/visits/*/sheet`); as demais rotas de visita continuam fechadas para ele.

## D-097 — Telas de chegadas e ficha

- **Decisão:** `/chegadas` para ADMIN ("Chegadas", com seletor de data iniciado em hoje no fuso da operação), PROSPECTOR e HOST ("Chegadas de hoje", pedido sem `date`, cabeçalho com o `date` da resposta); `/chegadas/:visitId/ficha` para os três. Menus na ordem da §16.1. A visita `COMPLETED` oferece "Ver ficha" ao ADMIN e ao PROSPECTOR (confirmado); a visita só chega a `COMPLETED` pelo registro da entrada.
- **Polling:** `refetchInterval` de 30 s, só com a lista de hoje; desligado ao sair da tela. Os tratamentos de 401, troca de senha e falha de rede ou 5xx são os da D-095.
- **Ficha e impressão:** idade como "1 ano", "N anos", "menos de 1 ano" para bebês e "—" sem data de nascimento; ausentes sem idade; "Nenhum" e "Sem observações" nos vazios. Impressão por `@media print` com `@page { size: A4; margin: 12mm }`: cabeçalho do sistema, menu, botões e avisos ficam de fora (variante `print:` do Tailwind), sem biblioteca. Verificado no Chromium com o pior caso (6 acompanhantes, limite padrão de `APP_MAX_COMPANIONS`, e 2.000 caracteres de observações): uma página A4, com folga.
- **Celular:** as células da lista quebram linha e o título da coluna de presentes fica "Presentes" em telas estreitas (o nome acessível continua "Acompanhantes presentes"), para o link da ficha não sair da tela.

## D-098 — Definições do dashboard

- **Período:** `from` e `to` (`YYYY-MM-DD`, os dois extremos incluídos), em dias de `APP_TIMEZONE`; sem parâmetros, os 30 dias até hoje (hoje − 29 a hoje); máximo de 366 dias (400 `PERIOD_TOO_LONG`); só uma das datas ou `from > to` é 400 `VALIDATION_ERROR`; data malformada, 400 `BAD_REQUEST` (D-095). Datas futuras valem, porque há visitas agendadas até 12 meses à frente (D-074). As séries trazem todos os dias do período, inclusive os de valor zero, em ordem crescente. A resposta traz `from`, `to` e `today`.
- **Crédito (D-013):** visitas, convites e entradas contam por `visits.prospector_id`; Leads contam pelo dono atual (`leads.prospector_id`). O filtro `prospectorId` do ADMIN usa a mesma regra; o PROSPECTOR vê sempre o Prospector da sessão, pode escolher `from` e `to`, e qualquer `prospectorId`, mesmo o próprio, é 403 `ACCESS_DENIED` (confirmado). `prospectorId` inexistente para o ADMIN: 404 `PROSPECTOR_NOT_FOUND`. GATE e HOST: 403; `access-by-day` só ADMIN.
- **Indicadores** (fotografia = estado atual; período = contagem entre `from` e `to`):
  - *Total de Leads*, *Leads atribuídos* e *Meus Leads*: fotografia, sem Leads `CANCELLED` (D-008, confirmado); atribuídos = com `prospector_id`.
  - *Visitas agendadas*: fotografia, `SCHEDULED` com `scheduled_date >= hoje`; uma `SCHEDULED` de data passada (job ainda não rodou, ou legada) não conta.
  - *Visitas hoje*: fotografia, `scheduled_date = hoje` com status `SCHEDULED` ou `COMPLETED`.
  - *Convites ativos*: fotografia, `ACTIVE` com `expires_at > agora`, o critério da Portaria: um convite vencido que o job não processou não conta.
  - *Visitas realizadas*, *No-show*, *Cancelamentos*: período pela `scheduled_date`, com status `COMPLETED`, `NO_SHOW` e `CANCELLED` com `cancel_reason <> 'RESCHEDULED'`.
  - *Pessoas recebidas* (a §16.7 chama de "Entradas realizadas"; rótulo trocado na aprovação do frontend da Fase 8, porque o número conta pessoas, não registros; o campo da API continua `entries`): período pelo `entry_at` em `APP_TIMEZONE`; o Lead mais os acompanhantes presentes de cada entrada liberada (confirmado).
  - *Próximas visitas* (PROSPECTOR): `SCHEDULED` a partir de hoje, pela leitura da D-041 (responsável ou dono atual), no máximo 10, por data, nome do Lead e id; `canEdit` só para o dono atual (D-078). Pode diferir do cartão "Visitas agendadas", que conta por crédito.
  - *Visitas por dia*: `scheduled_date`, com agendadas, realizadas, no-show e cancelamentos (sem remarcação).
  - *Acessos por dia* (só ADMIN): registros liberados pelo `entry_at` e negados pelo `created_at`, ambos em `APP_TIMEZONE`; com o filtro de Prospector entram só os registros ligados a um convite dele, então `INVALID_CODE` aparece só na visão geral.
- **Motivo do cancelamento (confirmado na aprovação):** coluna `visits.cancel_reason` (`RESCHEDULED`, `CANCELLED_BY_USER`, `LEAD_DISCARDED`), preenchida se e somente se `status = 'CANCELLED'` (CHECKs da V10) pelas três transações que cancelam (D-076, cancelamento e descarte do Lead, D-040). A V10 classifica as canceladas anteriores pela auditoria: `VISIT_RESCHEDULED` vira `RESCHEDULED`, `VISIT_CANCELLED` com `reason: LEAD_DISCARDED` no metadado vira `LEAD_DISCARDED`, e as demais ficam `CANCELLED_BY_USER` (ajuste pedido na revisão do PR). A auditoria não é fonte de estado do domínio: a exportação (Fase 9) precisa distinguir remarcação de cancelamento, e uma retenção futura da auditoria mudaria os números em silêncio.
- **Consultas:** SQL agregado via `JdbcClient`, sem carregar entidades e com número fixo de consultas por requisição (verificado com estatísticas do Hibernate e um contador de statements no perfil `test`); o `ViewerResolver` passou a ler só o id do Prospector. Só índices já existentes. Leitura sem auditoria.

## D-099 — Filtro de status do dashboard retirado

- **Decisão:** a §16.7 lista "status" entre os filtros do ADMIN, mas cada indicador de visita já é o recorte por um status (agendadas, realizadas, no-show, cancelamentos). Um filtro de status zeraria os demais cartões ou teria dois significados na mesma tela. Os filtros do ADMIN são período e Prospector (confirmado na aprovação da Fase 8).

## D-100 — Telas do dashboard

- **Decisão:** `/dashboard` é a tela inicial do ADMIN e do PROSPECTOR (§16.1). O ADMIN tem filtros de período (duas datas, iniciando nos 30 dias até hoje pela D-080) e Prospector numa linha acima dos números, aplicados juntos ao resumo e às duas séries; período com início depois do fim não é enviado e mostra a mensagem. O PROSPECTOR não tem filtros e não envia parâmetros: cinco cartões, próximas visitas (link para a visita e para a Agenda) e as próprias visitas por dia. Cada cartão diz se é fotografia ("agora", "de hoje em diante") ou contagem ("no período", "últimos 30 dias").
- **Gráficos:** Recharts `3.10.1` e `react-is` `19.3.0` (a mesma versão do React, par exigido pelo Recharts), fixados no `package.json`. Colunas empilhadas por dia, com um só eixo, todos os dias do período e barras de no máximo 24 px. Cores pelos slots categóricos 1 a 4 da paleta validada (azul, laranja, verde-água, amarelo), em ordem fixa por série: Agendadas, Realizadas, No-show, Cancelamentos; Liberados e Negados usam os slots 1 e 2. O validador de paleta aprovou as quatro cores para pares adjacentes (CVD e visão normal), com contraste abaixo de 3:1 no verde-água e no amarelo: por isso cada gráfico tem legenda sempre visível, tooltip e a tabela equivalente ("Ver tabela"), com todos os dias em DD/MM/AAAA. Texto da legenda na cor de texto; a cor da série fica só no marcador.
- **Datas:** o período e as tabelas usam DD/MM/AAAA; o campo de data é o nativo do navegador, que segue o idioma do aparelho (DD/MM/AAAA em pt-BR).
- **Removido:** `ComingSoonPage`, o marcador "Em breve" das Fases 2 a 7, sem uso depois do dashboard.

## D-101 — Exportações CSV

- **Decisão:** `GET /api/exports/{leads|visits|companions|access}`, só ADMIN, com `from`, `to`, `status` e `prospectorId` opcionais. Sem período, todo o histórico, sem limite de dias (confirmado); com período, as duas datas, `from ≤ to`, em dias de `APP_TIMEZONE` com os dois extremos incluídos. Só uma data ou `from > to`: 400 `VALIDATION_ERROR`; data ou status malformado (inclusive status de outro arquivo): 400 `BAD_REQUEST`; `prospectorId` inexistente: 404 `PROSPECTOR_NOT_FOUND`.
- **Arquivos** (cabeçalho em português; datas DD/MM/AAAA, data e hora DD/MM/AAAA HH:mm no fuso da operação; status, motivos e parentesco com os rótulos da interface; CPF completo e formatado `000.000.000-00`, para o Excel não tratá-lo como número):
  - `leads-AAAA-MM-DD.csv`: ID; Nome; CPF; Telefone; E-mail; Nascimento; Status; Prospector; Criado em. Período pelo `created_at`; status do Lead (inclui Descartado); Prospector pelo dono atual (como os Leads do dashboard, D-098).
  - `visitas-AAAA-MM-DD.csv`: ID; Lead; CPF do Lead; Prospector; Data; Status; Motivo do cancelamento; Acompanhantes; Entrada em. Período pela data da visita; status da visita; Prospector pelo responsável (D-013). O motivo vem do `cancel_reason` (Cancelada pelo usuário, Remarcação, Lead descartado); a visita antiga de uma remarcação aparece como Cancelada, motivo Remarcação.
  - `acompanhantes-AAAA-MM-DD.csv`: ID da visita; Data da visita; Lead; Nome; CPF; Nascimento; Parentesco; Presente. "Data da visita" acrescentada à §18 (confirmado). Filtros como os de visitas. "Presente" é Sim ou Não só na visita realizada; vazio nos demais status (confirmado).
  - `acessos-AAAA-MM-DD.csv`: Data e hora; Resultado; Motivo; Lead; Porteiro; Portaria; Acompanhantes presentes. Período pelo `created_at` da tentativa (numa entrada, igual ao `entry_at`); status é o resultado; Prospector pelo responsável da visita do convite, e com esse filtro a negativa sem convite (`INVALID_CODE`) sai, como no dashboard. `INVALID_CODE` aparece sem Lead. O código tentado nunca é lido nem escrito (D-044).
- **Formato (D-019, D-068):** BOM UTF-8, separador `;`, CRLF, `text/csv; charset=UTF-8`, `Content-Disposition: attachment` com o nome e a data de hoje em `APP_TIMEZONE`, `Cache-Control: no-store`. Cada célula passa primeiro pela proteção contra fórmula e depois pelas aspas do RFC 4180: o valor que começa com `=`, `+`, `-`, `@`, `\t` ou `\r` recebe o prefixo `'`; o que contém `;`, `"`, `\n`, `\r` ou `\t` sai entre aspas, com as aspas dobradas. Com `\t` ou `\r` no início, o apóstrofo fica dentro das aspas. A regra é única, sem exceção por coluna (confirmado): um telefone `+55 …` sai como `'+55 …`, e o apóstrofo fica visível no Excel e no sistema que importar o arquivo.
- **Auditoria e streaming (confirmado na aprovação):** o controller captura o id do ADMIN e o IP. Na thread da requisição, o service abre uma conexão própria em REPEATABLE READ somente leitura, conta as linhas e grava `EXPORT_GENERATED` numa transação própria (`entity_type = 'EXPORT'`, metadata `{ type, filters: { from, to, status, prospectorId }, rows }`, sem nenhum dado pessoal), confirmada antes do primeiro byte. Se a auditoria falhar, a conexão é devolvida e a resposta é erro (Problem Details), sem nenhuma linha. Depois, o corpo (`StreamingResponseBody`, em outra thread) lê as linhas do mesmo instantâneo da contagem com cursor (`fetchSize` 500) e escreve direto na resposta, sem carregar entidades nem montar o arquivo em memória. É a exceção à regra de auditar na mesma transação: auditar no fim perderia o registro de um download interrompido depois de parte dos dados ter saído. O `rows` da auditoria é exato mesmo com escrita concorrente: a contagem e o envio usam a mesma conexão e a mesma transação REPEATABLE READ, passada da thread da requisição para a do streaming, e só a auditoria roda numa transação à parte (testado em `ExportConsistencyTest`, com um Lead gravado entre a auditoria e o primeiro byte).
- **Tempo-limite e conexão:** `spring.mvc.async.request-timeout: 30m` (o padrão do Tomcat, 30 s, cortaria o download da base inteira). A conexão do banco fica presa durante todo o download e volta ao pool no fim, também se o cliente desistir; é aceito, porque as exportações são raras e só do ADMIN.

## D-102 — Consulta da auditoria

- **Decisão:** `GET /api/audit`, só ADMIN, com `from` e `to` pelo `created_at` em `APP_TIMEZONE` (padrão dos 30 dias até hoje, máximo de 366, `PERIOD_TOO_LONG`), `userId`, `action` (validada contra `AuditAction`), `entityType` (validado contra `USER`, `PROSPECTOR`, `LEAD`, `VISIT`, `INVITATION`, `ACCESS_RECORD`, `EXPORT`) e `entityId`; valor inválido é 400 `VALIDATION_ERROR`. Paginação com `page` e `size` (máximo 100), da mais recente para a mais antiga (`created_at` e `id` decrescentes). Cada linha: `id`, `createdAt`, `userId`, `userName` ("Sistema" quando `user_id` é nulo), `action`, `entityType`, `entityId`, `metadata` (objeto JSON) e `ipAddress`, numa consulta com junção a `users`.
- **A consulta não é auditada (confirmado):** a §19 não lista essa ação e o CHECK de `audit_logs.action` não a aceita.

## D-103 — Telas de Exportações e Auditoria

- **Decisão:** menu do ADMIN na ordem da §16.1, com "Exportações" antes de "Usuários" e "Auditoria" por último; as duas rotas são só do ADMIN.
- **Exportações (`/exportacoes`):** um bloco por arquivo, com descrição, a data que o período usa, período (as duas datas ou nenhuma), status do próprio arquivo (do Lead, da visita ou o resultado do acesso) e Prospector. Só os filtros preenchidos vão na URL. O download usa `fetch` com a sessão, salva com o nome do `Content-Disposition` e revoga a URL do Blob logo depois, porque o arquivo traz CPF completo. Período com uma data só ou invertido bloqueia o botão; erros da API aparecem em português no bloco.
- **Auditoria (`/auditoria`):** filtros de período (iniciando nos 30 dias até hoje, D-080), usuário, ação e entidade, com rótulos em português; trocar um filtro volta à primeira página, e a paginação mantém os filtros. Tabela com data e hora no fuso da operação, usuário ("Sistema" vem da API), ação, entidade com o início do id, detalhes e IP. O metadata aparece como pares "Chave: valor" em português, com status, causas e tipos traduzidos, datas em DD/MM/AAAA, sem os valores nulos, e objetos aninhados como "Filtros · Chave". A ordem de leitura segue uma lista fixa de chaves, porque o `jsonb` do PostgreSQL reordena as chaves (achado na verificação manual); chaves desconhecidas vêm depois, como vieram. Metadata vazio é "—".

## D-104 — E2E com Playwright

- **Decisão:** `scripts/e2e.sh`, fora do `verify.sh`, e um job `e2e` no `ci.yml`, em paralelo ao `verify`, nos mesmos gatilhos (confirmado). O script:
  - recusa começar com as portas 5433, 8080 ou 4173 ocupadas, ou com `waitForTimeout` em `frontend/e2e`;
  - gera o jar sem testes e o build do frontend (`E2E_SKIP_BUILD=1` reaproveita os dois);
  - sobe um PostgreSQL 16 descartável, com os dados em memória (`--tmpfs`), apagado no fim em qualquer caso: sucesso, falha de teste ou interrupção (Ctrl+C, SIGTERM do CI). A limpeza para o `vite preview` e o backend e espera cada um sair (SIGKILL depois de 30 s), antes de remover o container;
  - sobe o jar com perfil `dev`, apontado para esse banco, e o build de produção no `vite preview`, que repassa `/api` para a 8080 (sem mudar o `vite.config`, confirmado);
  - gera a senha inicial e a senha final do ADMIN a cada execução.
- **Onde fica o código:** `frontend/e2e` e `frontend/playwright.config.ts`, com `@playwright/test` fixado em `1.63.0`. O Vitest só lê `src/**/*.test.{ts,tsx}`, e o `tsconfig.e2e.json` põe o E2E no typecheck e no lint. `E2E_CHROMIUM_PATH` permite usar um Chromium já instalado; o CI usa o do `playwright install`.
- **Banco limpo e dados:** o perfil `dev` não carrega dados de exemplo; só o `BootstrapAdminInitializer` roda na subida, e ele cria o ADMIN inicial. A preparação global confere isso antes dos testes: 1 usuário e nenhum Lead, visita ou convite. Depois ela:
  - faz o login do ADMIN, esperando o login funcionar em vez do health, por causa da D-059, com no máximo 4 tentativas para não chegar ao limite de login;
  - troca a senha inicial;
  - recusa rodar entre 23:55 e 00:20 no fuso da operação, porque os fluxos agendam para hoje e o job noturno roda às 00:15 (confirmado).

  Cada teste cria os próprios usuários e Leads pela API: nomes "… E2E <sufixo>", CPFs válidos gerados por algoritmo e e-mails `@e2e.local`. A troca da senha provisória também é pela API. Nenhum teste depende de outro, e o limite de 30 validações por minuto por porteiro não interfere na repetição.
- **Contextos e esperas:**
  - um contexto de navegador por perfil, com login pela tela, fuso `America/Sao_Paulo` e `pt-BR`;
  - a Portaria usa o viewport do Pixel 7 e fica sem permissão de câmera, então a validação é pela digitação;
  - só esperas por condição, sem novas tentativas (`retries: 0`);
  - a chegada para o anfitrião é esperada por até 35 s: o intervalo de 30 s mais a folga da requisição.
- **Testes:**
  - E1, o fluxo da §23. Lead e acompanhantes têm CPF, e a ficha é conferida, na página e na resposta da API, sem o CPF com e sem pontuação e sem os 6 dígitos que a máscara mostraria.
  - E2, remarcação e reemissão invalidando o código anterior.
  - E3, primeiro acesso com troca obrigatória, e saída que invalida a sessão no servidor.
  - E4, leitura do QR real pela câmera simulada do Chromium: o PNG da API vira um vídeo Y4M decodificado num canvas, sem dependência nova. O vídeo fica num diretório temporário, porque o Chromium não abre o arquivo num caminho com acentos (achado na implementação). O navegador da câmera é o Chromium completo (`channel: 'chromium'`): no `chromium-headless-shell`, padrão do Playwright para testes headless, a câmera simulada não leu o QR no CI; com o Chromium completo, passou.
- **Estabilidade (medida em 2026-09-26):** 20 repetições da suíte com 4 workers passaram (80 de 80, 5,4 min). Em cada teste:

  | Teste | Tempo |
  |---|---|
  | E1 | 36 a 43 s (quase tudo é a espera pela consulta de 30 s) |
  | E2 | 7 a 14 s |
  | E3 | 5 a 9 s |
  | E4 | 5 a 12 s |

  Uma execução do script com jar e build prontos leva cerca de 1 min. O E4 ficaria de fora se não passasse nas 20 repetições (condição da aprovação).
- **`dev` do E2E × `prod`:**

  | Ponto | E2E (`dev`) | `prod` |
  |---|---|---|
  | Cookies de sessão e `XSRF-TOKEN` | sem `Secure`, por HTTP | `Secure`, só com HTTPS (D-054) |
  | Swagger e `/v3/api-docs` | ligados (D-056) | inexistentes |
  | Credenciais | valores padrão fictícios, trocados pelo script | variáveis obrigatórias, sem padrão (D-052) |
  | IP do cliente | o do `vite preview` (127.0.0.1) | via `forward-headers-strategy: native` atrás do Nginx |
  | Servidor da frente | `vite preview` | Nginx |
  | Nome do Resort | sem meta preenchida (nome padrão "Resort") | `RESORT_NAME` injetada pelo Nginx (D-087) |
  | Dados de exemplo | nenhum | nenhum |
  | Job noturno e fuso | ligado, `APP_TIMEZONE` padrão | iguais |
- **O que o E2E não cobre:**
  - HTTPS e Nginx, com seus cabeçalhos (HSTS, CSP, `Permissions-Policy`, `X-Content-Type-Options`);
  - cookies `Secure`;
  - o IP real atrás do proxy, a prontidão da D-059 e o usuário do banco sem ownership (D-057), todos da Fase 11;
  - navegadores além do Chromium, como o Safari do iPhone;
  - câmeras e aparelhos reais: o Pixel 7 é só o viewport;
  - a Web Share API;
  - a impressão A4 da ficha (verificação manual, D-097).
- **Acréscimos (Fase 11a):**
  - **E5** percorre as telas de cada perfil.
  - **E6** baixa e compartilha a imagem do convite (D-087): "Baixar", "Compartilhar" sem Web Share API (baixa) e "Compartilhar" com a Web Share API simulada. Nos três casos, confere que o arquivo é um PNG de 1080 × 1440 cujo QR decodifica `RSV:<código>`, com o `@zxing/library` (já no projeto pelo `@zxing/browser`, agora declarado como devDependency na mesma versão).
  - Todos os contextos reprovam qualquer violação de CSP.
  - `E2E_BASE_URL` roda a suíte contra a pilha de produção por HTTPS (D-105), e `E2E_SENSITIVE_FILE` registra os CPFs e códigos usados (D-111).

## D-105 — Imagens e pilha de produção

- **Imagens:** bases fixadas por digest. Argumentos de build (`JDK_IMAGE`, `JRE_IMAGE`, `NODE_IMAGE`, `NGINX_IMAGE`) permitem trocar o registro, por exemplo por um espelho, sem editar os arquivos.
  - **Backend** (`backend/Dockerfile`): build com `eclipse-temurin:21-jdk-alpine` e runtime com `eclipse-temurin:21-jre-alpine`. O jar é extraído nas camadas do Spring Boot como `app.jar`, e o processo roda como o usuário `app` (uid 10001), com `TZ=UTC` e perfil `prod`.
  - **Nginx de borda** (`nginx/Dockerfile`, contexto na raiz): build do frontend com `node:22-alpine` e serviço com `nginxinc/nginx-unprivileged` (uid 101), com o nome do Resort injetado na subida (D-087).
- **`docker-compose.prod.yml`:**
  - Serviços: `postgres`, `migrate` (D-057), `backend` e `nginx`, todos numa rede interna com sub-rede fixa. Só o Nginx publica porta.
  - Ordem de subida: o backend espera o postgres saudável e o `migrate` concluído; o Nginx espera o backend saudável, pelo readiness (D-059).
  - Todos os serviços têm `json-file` com `max-size` de 10 MB e `max-file` 5. Os de longa duração têm `restart: unless-stopped`, healthcheck e limite de memória.
  - As variáveis obrigatórias param a subida se faltarem. O modelo é o `.env.prod.example`, sem nenhum valor real, separado do `.env.example` de desenvolvimento (confirmado).
- **Memória** (meta de 2 GB, confirmado):

  | Serviço | Limite | Ajustes |
  |---|---|---|
  | backend | 768 MB | `-XX:MaxRAMPercentage=60` (heap máximo medido: 462 MB), `-XX:+UseSerialGC`, `-Xss512k`, `-XX:+ExitOnOutOfMemoryError` |
  | postgres | 512 MB | `shared_buffers=128MB`, `effective_cache_size=384MB`, `work_mem=4MB`, `maintenance_work_mem=64MB`, `max_connections=30` |
  | nginx | 64 MB | — |
  | migrate | 384 MB | — |

  Perfil de 1 GB, documentado no `.env.prod.example`: backend com 448 MB, postgres com 256 MB e `shared_buffers=64MB`, mais 1 GB de swap. Uso medido na verificação, logo depois do login e da criação de um Lead: backend com cerca de 310 MB, postgres com 48 MB e nginx com 5 MB.
- **Verificação** (`scripts/prod-check.sh`, job `prod-stack` do CI em paralelo aos demais; vira check obrigatório depois de três execuções verdes):
  - constrói as imagens e sobe a pilha com um `.env` descartável e senhas aleatórias, na porta 18080;
  - confere a configuração do compose, a subida e o `migrate`, o login logo depois do readiness, os cookies `Secure`, a troca de senha e a criação de Lead como `resort_app`, o health público, os 404 do actuator, os usuários sem root, o nome do Resort e a memória;
  - derruba tudo e apaga os volumes em qualquer saída.

  Mutações detectadas: o backend publicando uma porta e o nome sem o escape das aspas. O HTTPS, os cabeçalhos, o IP real, o backup e o runbook entram nos PRs 2 e 3.

## D-106 — "Hoje" da operação na validação de datas e na importação

- **Contexto:** na releitura da Fase 11, dois pontos comparavam com o dia do fuso da JVM, e não com o de `APP_TIMEZONE`:
  - a importação de Leads (`LocalDate.now()`);
  - o `@PastOrPresent` do nascimento de Lead e acompanhante, cujo relógio padrão é o do sistema.

  Num container em UTC, entre 21h e meia-noite em São Paulo, a data de amanhã passava como válida.
- **Decisão:**
  - O Bean Validation recebe um `ClockProvider` com o `Clock` da aplicação no fuso da operação (`ValidationConfigurationCustomizer` no `CommonConfig`).
  - A importação usa o `BusinessCalendar.today()`.
  - Os containers rodam com `TZ=UTC`, e o comportamento não depende disso.
- **Testes:** o `OperationDayValidationTest` roda com o fuso padrão da JVM em `Pacific/Kiritimati` e o relógio às 22:30 de 19/09 em São Paulo (20/09 em UTC). Em Lead, acompanhante e importação, 19/09 é aceito e 20/09 é recusado. As mutações (o validador sem o relógio da operação e a importação com `LocalDate.now()`) derrubam os três casos.

## D-107 — Nginx de borda: TLS, cabeçalhos, CSP, cache e limites

- **Portas:**
  - 8080 no container, publicada como 80, só responde ao desafio do Let's Encrypt (`/.well-known/acme-challenge/`, por HTTP) e ao healthcheck; todo o resto recebe 301 para HTTPS, com caminho e query;
  - 8443, publicada como 443, é o HTTPS com HTTP/2.

  Fora da porta 443, a verificação local mantém a porta no redirecionamento (`PUBLIC_HTTPS_PORT`).
- **TLS:** configuração "intermediate" da Mozilla sem suítes DHE: só TLS 1.2 e 1.3, cifras ECDHE com AEAD, sem tickets de sessão. Sem OCSP stapling, porque o Let's Encrypt encerrou o OCSP em 2025.
- **Cabeçalhos:** em todas as respostas, inclusive erros, com `always` e incluídos em cada location. O backend manda os mesmos pelo Spring Security; eles são escondidos (`proxy_hide_header`) para não sair em dobro, e o HSTS do Spring traria `includeSubDomains`.
  - `Strict-Transport-Security: max-age=31536000`, com `includeSubDomains` e `preload` ligáveis por variável (confirmado);
  - `Content-Security-Policy` (ver abaixo);
  - `Permissions-Policy: camera=(self), microphone=(), geolocation=(), payment=(), usb=()` (D-093);
  - `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin` e `X-Frame-Options: DENY`.
- **CSP:**

  ```
  default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:;
  connect-src 'self'; font-src 'self'; media-src 'self'; worker-src 'self'; object-src 'none';
  base-uri 'self'; form-action 'self'; frame-ancestors 'none'
  ```

  - `style-src 'unsafe-inline'` é necessário porque o sonner e o Radix injetam `<style>` com conteúdo variável (confirmado); os scripts continuam só do próprio site.
  - `img-src blob:` serve para o QR do convite. O vídeo da câmera usa `srcObject` e não precisa de `media-src blob:`; `data:` e `worker-src blob:`, previstos no plano, ficaram de fora porque nada os usa.
  - O E2E registra as violações do console do Chromium em todos os contextos e reprova o teste em qualquer uma. A suíte inteira roda contra a pilha por HTTPS, inclusive o E5, que percorre as telas de cada perfil com gráficos e diálogo.
  - A mutação que tirou `'unsafe-inline'` foi detectada pelo E2E.
- **SPA e cache:**
  - `try_files $uri /index.html` fora de `/api`, `/actuator` e `/v3`;
  - `/assets/*` com `public, max-age=31536000, immutable`, e arquivo inexistente é 404;
  - `index.html` e as rotas da SPA com `no-cache`.
- **Rotas do backend:** só `/api/` e `/actuator/health` (encaminhado ao readiness interno, D-059) chegam a ele. `/actuator/*` e `/v3/*` dão 404.
- **Limites:**
  - `client_max_body_size 6m` em `/api/`: a importação de 4,9 MB passa, e 7 MB recebe 413 do próprio Nginx;
  - `/api/exports/` com `proxy_buffering off` e `proxy_read_timeout`/`proxy_send_timeout` de 1900 s, acima dos 30 min da D-101. A verificação lê o `nginx -T`; o envio aos poucos de uma exportação grande não é medido.
- **Log de acesso:** formato próprio, `$remote_addr [$time_local] "$request_method $uri $server_protocol" $status $body_bytes_sent $request_time`, sem query string, Referer nem User-Agent, porque `/api/leads?cpf=…` e a busca podem levar CPF. O log de erro do Nginx incluiria a linha do pedido, com a query, numa falha de upstream; em `/api/` ele fica só no nível `crit` (D-111).
- **Imagem e verificação:** o `nginx-unprivileged` continua sem root. `server_tokens off`. O `prod-check.sh` confere tudo isso por fora. A CSP, o HSTS, a `Permissions-Policy` e os demais cabeçalhos são conferidos, cada um uma única vez, em HTML, rota da SPA, asset, asset inexistente, API, actuator e health.

## D-108 — IP real do cliente

- **Na pilha:**
  - O Nginx tem IP fixo na rede interna (`NGINX_INTERNAL_IP`, padrão 172.30.0.10) e **sobrescreve** o `X-Forwarded-For` com o `$remote_addr` que viu, descartando o que o cliente mandar.
  - No perfil `prod`, o Tomcat usa `forward-headers-strategy: native` com `internal-proxies` igual a `\Q${TRUSTED_PROXY_IP}\E`: só o IP do Nginx, comparado literalmente. O padrão do Tomcat confia em toda a faixa 172.16/12, que inclui as redes do Docker.
  - Assim, o `getRemoteAddr()` já usado no login, na exportação e na auditoria é o IP do cliente, e o limite de login (D-017) passa a contar por ele.
- **Testes:**
  - `ProductionForwardedIpTest`: o perfil `prod` com Tomcat real aceita o `X-Forwarded-For` só vindo do proxy confiável e o ignora vindo de outro endereço. Sem a propriedade `internal-proxies`, o teste falha.
  - `prod-check.sh`:
    - um login com `X-Forwarded-For` forjado grava na auditoria o IP que o Nginx viu, e não o forjado nem o do Nginx;
    - cinco falhas trocando o IP forjado levam a 6ª tentativa a 429;
    - sem a sobrescrita no Nginx, o IP forjado vai para a auditoria (mutação detectada).
- **Como o Docker publica as portas e quando o IP de origem se perde:**
  - Uma porta publicada vira uma regra DNAT do iptables (cadeias `DOCKER`/`DOCKER-USER`). O pacote de um cliente externo chega ao container com o **IP de origem preservado**.
  - O `docker-proxy` (userland-proxy, ligado por padrão) atende ao que o DNAT não cobre: conexões feitas do próprio host (localhost e hairpin) e IPv6 quando o Docker não tem IPv6 na rede do container. Nesses casos, o Nginx vê o IP do gateway da rede do Docker (por exemplo, 172.30.0.1), e não o do cliente.
  - Na verificação local, o cliente é o próprio host, por isso o IP visto é o do gateway (172.30.250.1). A verificação prova a cadeia (o IP que o Nginx vê é o gravado), não o IP público.
- **Configuração para preservar o IP:**
  - portas publicadas explicitamente em IPv4 (`HTTP_BIND`/`HTTPS_BIND`, padrão `0.0.0.0`), para o Docker não abrir o IPv6 pelo `docker-proxy`;
  - na VPS, `"userland-proxy": false` no `/etc/docker/daemon.json`;
  - sem registro AAAA para o domínio, a menos que o IPv6 do Docker seja configurado com `ip6tables`.
- **ufw:** as portas publicadas pelo Docker não passam pelas regras do ufw, porque as regras do Docker entram antes, na tabela nat e na cadeia `DOCKER-USER`. Por isso **nenhuma porta além de 80 e 443 pode ser publicada**. PostgreSQL, backend e a porta 8081 não têm `ports`, e o `prod-check.sh` falha se algum serviço além do Nginx publicar porta.
- **Verificação da 11b (runbook do PR 3):**
  - o log do Nginx mostra IPs públicos, não 172.x;
  - `ss -ltnp` não mostra `docker-proxy`;
  - login a partir de um celular fora da rede do servidor, conferindo na auditoria que o IP gravado é o público do celular.

## D-109 — Certificados

- **Let's Encrypt, desafio HTTP-01 por webroot:**
  - O volume `acme-webroot` é compartilhado entre o `certbot` (leitura e escrita) e o Nginx (só leitura); o volume `letsencrypt` guarda os certificados.
  - `scripts/issue-cert.sh <.env.prod>` faz a primeira emissão, com `DOMAIN`, `LETSENCRYPT_EMAIL` e `LETSENCRYPT_STAGING` (`true` usa o ambiente de testes, recomendado primeiro), e reinicia o Nginx. `--print` mostra o comando sem executar.
  - O serviço `certbot`, no perfil `letsencrypt` (`COMPOSE_PROFILES=letsencrypt` no `.env.prod`), roda `certbot renew` a cada 12 h.
  - O Nginx recarrega a cada 6 h para pegar o certificado renovado.
- **Primeira subida:** sem certificado para `DOMAIN`, o Nginx usa um provisório autoassinado, gerado no build da imagem e que nenhum navegador aceita. Com ele, o Nginx sobe e responde ao primeiro desafio.
- **Local:** `scripts/local-cert.sh` gera uma CA descartável e um certificado para `localhost` no layout do Let's Encrypt. O `prod-check.sh` o coloca no volume `letsencrypt` e confere que o `curl` o aceita com a CA e o recusa sem ela. A pilha local não deve ser aberta num navegador de uso pessoal, porque o HSTS de `https://localhost` ficaria gravado nele.
- **Imagens:** `certbot/certbot:v5.8.0` e `alpine:3.22.6` (estágio do certificado provisório), fixadas por digest.

## D-110 — Backup e restauração

- **Serviço `backup`:** a imagem própria parte da mesma imagem do PostgreSQL da pilha, para que `pg_dump` e `pg_restore` tenham a mesma versão do banco. Ela traz o `age` 1.2.1 do release oficial, conferido por sha256 (amd64 e arm64).
  - O `crond` roda o `backup.sh` todos os dias às `BACKUP_TIME` (padrão 03:00), no fuso da operação (`TZ=APP_TIMEZONE`).
  - O `backup.sh` faz `pg_dump -Fc` como `resort_backup` (só leitura, D-057), sem os dados de `spring_session*`, cifra com `age` para `BACKUP_AGE_RECIPIENT` e grava `resort-AAAAMMDDTHHMMSSZ.dump.age` no volume `backups`.
  - Retenção: arquivos com 14 dias ou mais são apagados.
  - O processo roda como root dentro do container, porque o `crond` do busybox exige; o container só alcança o banco e o volume de backups.
- **Verificação de saúde (acréscimo antes do merge do PR 3):** o healthcheck do container roda o `backup-health.sh`.
  - **Regra:**
    - falha se o `crond` estiver parado;
    - com algum backup no volume, o mais recente (`resort-*.dump.age`) precisa ter menos de `BACKUP_MAX_AGE_HOURS` (26 h: um dia mais folga), e um reinício do container não esconde um backup parado;
    - sem nenhum backup (pilha recém-criada), vale uma carência de 26 h desde a subida do container, porque o primeiro backup só sai no próximo `BACKUP_TIME`.
  - A mensagem de cada verificação diz o motivo e aparece no `docker inspect`.
  - O intervalo padrão é 5 min (`BACKUP_HEALTH_INTERVAL`), com 2 tentativas.
  - **O que fazer quando fica `unhealthy`:** runbook, seção 13.2.
  - **Testes no `prod-check.sh`,** pelo status do próprio Docker, com intervalo de 5 s e esperando também a mensagem da checagem mais recente, porque o status sozinho pode vir de uma checagem anterior (achado ao escrever o teste):
    - pilha nova sem backup: saudável pela carência;
    - subida marcada 27 h atrás e nenhum backup: `unhealthy`;
    - de volta à carência: saudável;
    - com backup recente: saudável;
    - último backup com 30 h: `unhealthy`, e continua assim depois de reiniciar o container;
    - backup em dia de novo: saudável.

    A mutação que voltou ao healthcheck antigo (`pgrep crond`) reprovou.
- **Chave:** só a pública fica no servidor. A privada é gerada e guardada pelo operador (gerenciador de senhas e cópia offline) e só vai ao servidor, em `/dev/shm`, durante uma restauração. Quem invadir a VPS não lê os backups; perder a chave privada torna todos os backups inúteis.
- **Restauração** (`scripts/restore.sh`, com o passo a passo no `docs/DEPLOY.md`):
  - confere que a chave decifra o backup antes de mexer em qualquer coisa;
  - para o Nginx, o backend e o backup;
  - recria o banco com as permissões de banco do `postgres/initdb`;
  - restaura como `resort_owner`;
  - roda o `migrate` e sobe a pilha.

  Serve para a mesma VPS e para uma VPS nova (os papéis vêm da criação do volume).
- **Cópia fora da VPS: obrigatória na 11b** (confirmado). O destino exato fica para a 11b. O modelo recomendado é o operador puxar os arquivos por `rsync` com uma chave SSH restrita ao `rrsync -ro`, sem credencial de destino no servidor. A cópia só na VPS não protege contra perda do servidor, do disco ou da conta, nem contra ransomware.
- **Testes no `prod-check.sh`:**
  - o horário do crontab e o fuso;
  - o arquivo começa com o cabeçalho do age;
  - sem a chave, o `pg_restore` não lê o arquivo;
  - com a chave, o dump traz o CPF de um Lead de teste.

    O formato custom do `pg_dump` já comprime os dados, então procurar o CPF no arquivo sozinho não provaria a criptografia (achado na mutação "sem criptografia").
  - retenção: um arquivo de 15 dias é apagado e um de 13 é mantido;
  - a restauração com a chave errada para com mensagem clara e sem mexer no banco;
  - **perda total**: `down -v` apaga tudo; a restauração parte da cópia do arquivo fora da pilha. Depois dela, as contagens por tabela e um hash do conteúdo de `audit_logs` batem com os de antes, o ADMIN entra, `resort_app` continua sem ownership e sem poder apagar `audit_logs`, e os triggers voltam.

## D-111 — Logs sem dado pessoal na produção

- **Retenção:** `json-file` com 10 MB × 5 por serviço (D-105), no máximo cerca de 50 MB por serviço.
- **Nginx:**
  - log de acesso sem query, Referer e User-Agent (D-107);
  - em `/api/`, `error_log` só no nível `crit`. O log de erro do Nginx grava a linha do pedido, com a query, quando o backend falha (`upstream timed out … request: "GET /api/leads?cpf=…"`); a falha continua visível pelo status 502/504 no log de acesso e pelos logs do backend.
  - Conectar ao backend tem limite de 5 s: fora do ar, a API responde 504 em 5 s, e não em 60 s.
- **Backend:** as regras das fases anteriores (regra 5 do CLAUDE.md, D-069).
- **Verificação no `prod-check.sh`:**
  - o E2E grava os CPFs e códigos de convite que usou (`E2E_SENSITIVE_FILE`);
  - o script faz de propósito um `GET /api/leads?cpf=…&q=…` e, com o backend parado, outro que recebe 504;
  - em seguida, procura cada CPF (com e sem pontuação), cada código (com e sem hífen) e as senhas do ADMIN e do superusuário nos logs de todos os containers;
  - a linha de acesso de `/api/leads` aparece sem a query.

  Mutações detectadas: sem o `error_log crit` (o CPF apareceu no log de erro) e o log de acesso com `$request` (o CPF apareceu na query).

## D-112 — Publicação das imagens

- **Workflow `release.yml`:** roda só no push de uma tag `vX.Y.Z`.
  - Primeiro executa os mesmos checks do `ci.yml`, chamado como workflow reutilizável: `verify`, `e2e` e `prod-stack`, para aquele commit.
  - O job `publish` só roda se os três passarem, e só se o commit da tag estiver na `main`.
  - O `scripts/publish-images.sh` gera `backend`, `nginx` e `backup` e publica cada uma em `ghcr.io/<dono>/<repositório>/<imagem>` com duas tags, `vX.Y.Z` e `sha-<commit>`, e com os rótulos OCI de origem, versão e revisão.
  - O `ci.yml` deixa de rodar no push de tags (`branches: ['**']`), para os checks da tag não rodarem duas vezes.
- **Terceira imagem:** o backup precisa do `age` junto do `pg_dump`, e não há imagem pronta com os dois. Por isso são três imagens, e não duas.
- **Servidor:** faz `docker login ghcr.io` com um token só de leitura (`read:packages`) e usa as tags de versão no `.env.prod` (`docs/DEPLOY.md`, seção 4). O repositório fica privado antes da 11b, e as imagens seguem a visibilidade dele.
- **Consumo** (medido na pilha local e estimado para o CI):

  | Item | Estimativa |
  |---|---|
  | Imagens comprimidas | backend 127 MB, nginx 22 MB, backup 120 MB; cerca de 270 MB no conjunto |
  | Uma release | os três checks (cerca de 4 + 2 + 6 min, em paralelo) mais o `publish` (cerca de 6 a 8 min, sem cache): em torno de 20 minutos de runner |

  Nas versões seguintes, as camadas de base e de dependências se repetem, e cada versão tende a acrescentar dezenas de MB, não o conjunto inteiro. Uma troca de imagem base ou de dependências acrescenta de novo perto do tamanho cheio.

  Enquanto o repositório é público, os minutos de Actions e o GHCR não são cobrados. Com ele privado, os minutos e o armazenamento de pacotes contam na cota do plano da conta; confira os valores atuais na página de cobrança do GitHub antes da 11b. Para não crescer sem limite, apague as versões antigas das imagens, mantendo pelo menos as duas últimas para a volta de versão.

  O maior consumo de minutos não é a release: cada push num branch com PR roda o CI duas vezes (`push` e `pull_request`), cerca de 24 minutos por push. Se a cota apertar com o repositório privado, restringir o `push` à `main` corta isso pela metade.
- **Não testado aqui:** os workflows só rodam no GitHub. A primeira tag de versão é o teste real, e dá para usar uma tag descartável.


## D-113 — Nome do produto: Resortric

- **Contexto:** o repositório no GitHub foi renomeado de `leasort` para `resortric`, o nome definitivo do produto.
- **Decisão:**
  - A única referência a "leasort" no repositório era o container descartável do E2E, que passa a se chamar `resortric-e2e-db` (`scripts/e2e.sh`).
  - O workflow de release e o `scripts/publish-images.sh` montam o prefixo das imagens a partir de `github.repository`. Por isso, a próxima tag publica em `ghcr.io/<dono>/resortric/{backend,nginx,backup}` sem mudança no código. `.env.prod.example` e `docs/DEPLOY.md` já usam `<dono>/<repositório>`.
  - Não mudam: o pacote Java `com.resort.platform`, o banco `resort`, os papéis `resort_owner`, `resort_app` e `resort_backup`, os volumes e o projeto do compose de produção (`name: resort`). Nenhum contém "leasort", e mudar qualquer um exigiria migrar uma instalação existente.
- **Projeto do compose:**
  - **Produção:** o `docker-compose.prod.yml` fixa `name: resort`. Por isso, containers, rede e volumes (`resort_postgres-data`, `resort_backups` etc.) não dependem do nome da pasta.
  - **Desenvolvimento:** o `docker-compose.yml` passa a fixar `name: resort-dev`, e o volume vira `resort-dev_postgres-data`.
    - Motivo: sem o nome fixo, o projeto vinha da pasta (`leasort` → `leasort_postgres-data`). Ao renomear a pasta, o compose criaria um volume vazio e deixaria o antigo órfão. O volume de dev deixa de depender do nome da pasta.
    - O nome é diferente do `resort` da produção para os dois não se confundirem na mesma máquina.
    - A passagem para quem já tinha o ambiente está no `CLAUDE.md`, em Problemas comuns.
- **Imagens publicadas como `leasort`:** as imagens da `v0.1.0` continuam em `ghcr.io/<dono>/leasort/*`. O GHCR não renomeia pacotes nem redireciona o nome antigo. Nenhuma instalação usa essas imagens, porque a 11b ainda não começou. A próxima versão sai com o nome novo; depois disso, os pacotes `leasort/*` podem ser apagados.
- **Descartado:**
  - Renomear o pacote Java, o banco ou os papéis: o ganho seria só estético, e o custo seria uma migração de dados.
  - Copiar o volume de dev antigo para o novo: os dados de dev são fictícios, e o backend recria as tabelas e o ADMIN inicial na subida.
- **Impacto:**
  - Nenhum na aplicação nem no banco de produção.
  - No dev, a troca de volume acontece uma única vez: o banco nasce vazio na primeira subida depois da atualização. Dali em diante, renomear a pasta não muda mais o volume.
