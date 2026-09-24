# CLAUDE.md

Sistema web de gestão de Leads, visitas, acompanhantes, convites e controle de acesso para um Resort. Monólito modular: Spring Boot + PostgreSQL no backend, React + TypeScript no frontend.

## Fonte da verdade

- `docs/SPEC.md` — o que construir. Ler as seções relevantes antes de cada fase.
- `docs/DECISIONS.md` — por que foi decidido assim. Consultar antes de propor qualquer mudança.
- Em conflito entre código e SPEC, a SPEC vence. Em conflito entre SPEC e DECISIONS, a decisão mais recente vence.

## Regras obrigatórias

1. Não ampliar o escopo. Nada da seção 3 "Fora do escopo" ou da seção 28 da SPEC.
2. Não criar CRM, conversão de Lead em Cliente, gestão de Sócios nem registro de resultado de venda.
3. O backend é a autoridade para permissões, carteira e validações. Nunca confiar só no frontend.
4. Nunca expor dado pessoal além da seção 15 da SPEC. Mascaramento é feito no DTO do backend.
5. Nenhum dado pessoal no QR. Nenhum CPF, senha ou código de convite em logs.
6. Toda mudança de banco é uma nova migration Flyway. Nunca editar migration já aplicada.
7. Não criar tabelas, dependências ou abstrações sem necessidade. Sem interface para service com implementação única.
8. Ambiguidade: escolher a solução mínima compatível com a SPEC e registrar em `docs/DECISIONS.md` como nova D-XXX.
9. Mudança de decisão existente: parar e perguntar antes. Nunca alterar silenciosamente.
10. Não inventar integrações externas. Não usar dados fictícios fora do perfil `dev`.
11. Toda funcionalidade crítica tem teste. Não declarar tarefa concluída sem build e testes passando.
12. Preservar funcionalidades existentes. Não remover código sem justificar e testar o impacto.

## Stack

- Backend: Java 21, Spring Boot 4.1.x (D-036), Maven wrapper, Spring Security + Spring Session JDBC, Spring Data JPA, Bean Validation, Flyway, springdoc-openapi, ZXing.
- Banco: PostgreSQL 16.
- Frontend: React, TypeScript, Vite, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS, shadcn/ui, Recharts, `@zxing/browser`.
- Testes: JUnit 5, Testcontainers, Vitest, Testing Library, Playwright.

## Estrutura

```text
backend/src/main/java/com/resort/platform/
  auth users prospectors leads visits invitations access arrivals
  dashboard exports imports audit common
frontend/src/
  app/ (rotas, layout por perfil)  features/<módulo>/  components/  lib/
docs/  nginx/  docker-compose.yml  docker-compose.prod.yml  .env.example
```

## Convenções

- Código, classes, tabelas e endpoints em inglês. Textos de interface em português do Brasil.
- Tabelas snake_case no plural; colunas snake_case; enums `UPPER_SNAKE_CASE`.
- Endpoints REST no plural, kebab-case, sob `/api`.
- Chaves primárias UUID. Datas-hora `timestamptz` em UTC; datas de negócio `date` no timezone `APP_TIMEZONE`.
- Erros em Problem Details com campo `code` estável.
- DTOs de entrada e saída separados das entidades. Entidades nunca saem do controller.
- Auditoria por chamada explícita a `AuditService` na mesma transação.
- Commits no formato `tipo(módulo): descrição` (feat, fix, test, refactor, docs, chore).

## Comandos

Requisitos: JDK 21 ou mais recente (o build gera bytecode para Java 21; também roda com JDK 25), Node 22+, Docker.

```bash
cp .env.example .env                   # opcional; os valores de dev do .env.example são os mesmos do application-dev.yml
docker compose up -d postgres          # banco de desenvolvimento (PostgreSQL 16)
cd backend && ./mvnw spring-boot:run   # API em :8080, perfil dev (só o plugin ativa dev; D-037)
cd backend && ./mvnw verify            # build + testes (Testcontainers; requer Docker)
cd frontend && npm run dev             # Vite em :5173 com proxy de /api para :8080
cd frontend && npm run lint            # oxlint
cd frontend && npm run typecheck       # tsc -b
cd frontend && npm test                # Vitest
./scripts/verify.sh                    # CI local completo; o GitHub Actions roda o mesmo script (D-035)
```

Health: `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`.
Swagger (só no perfil dev, D-056): `http://localhost:8080/swagger-ui/index.html`.
ADMIN inicial no dev: `admin@resort.local` / `admin-dev-password`, com troca obrigatória no primeiro acesso (D-052).
O jar exige perfil explícito: `SPRING_PROFILES_ACTIVE=prod java -jar backend/target/platform-*.jar`.
Playwright entra na Fase 10.

## Problemas comuns

- **`password authentication failed` ou conexão no banco errado (porta 5432 ocupada).** Um PostgreSQL instalado na máquina (comum no Windows) ocupa a porta 5432 e intercepta a conexão destinada ao container. Verifique com `Get-NetTCPConnection -LocalPort 5432` (PowerShell) ou `netstat -ano | findstr :5432`. Pare o serviço local (`Get-Service postgresql*` e `Stop-Service <nome>`, ou pelo `services.msc`) antes do `docker compose up -d postgres`, ou mude a porta do PostgreSQL local.
- **PowerShell bloqueia o `npm` (`execução de scripts foi desabilitada neste sistema`).** A política de execução padrão impede o `npm.ps1`. Libere para o usuário atual com `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`, ou chame `npm.cmd` no lugar de `npm`.
- **`.env` alterado e o backend não conecta.** O backend não lê o `.env`; só o `docker-compose.yml` lê. Se mudar credenciais no `.env`, exporte as mesmas variáveis (`DB_URL`, `DB_USER`, `DB_PASSWORD`) no terminal do backend. Se o volume do banco já foi criado com outra senha, recrie com `docker compose down -v` (apaga os dados de desenvolvimento).
- **No Windows, `./mvnw` e `./scripts/verify.sh`.** No PowerShell use `.\mvnw.cmd`; o `verify.sh` precisa de Git Bash ou WSL.

## Fluxo de trabalho

Implementar uma fase da seção 25 da SPEC por vez, nesta sequência:

1. Ler as seções da SPEC e as decisões ligadas à fase.
2. Apresentar um plano: arquivos a criar ou alterar, migrations, endpoints, testes. Aguardar aprovação antes de escrever código.
3. Implementar em passos pequenos, rodando os testes a cada passo.
4. Ao terminar: `./mvnw verify` e testes do frontend passando.
5. Resumir o que foi feito, o que ficou pendente e qualquer nova D-XXX registrada.
6. Commit da fase.

Não iniciar a fase seguinte sem aprovação.

## Pontos que exigem atenção redobrada

- Verificação de carteira do Prospector em todo acesso a Lead, visita e convite.
- `POST /api/access/register`: lock pessimista, revalidação completa e teste de concorrência.
- Cálculo de "hoje" sempre no timezone da operação, nunca no do servidor.
- Visibilidade de CPF e dados de acompanhantes por perfil.
- Imutabilidade de `audit_logs`.
