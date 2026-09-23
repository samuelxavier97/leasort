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

- Backend: Java 21, Spring Boot 3.x, Maven wrapper, Spring Security + Spring Session JDBC, Spring Data JPA, Bean Validation, Flyway, springdoc-openapi, ZXing.
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

Atualizar esta seção ao final da Fase 1 com os comandos reais.

```bash
docker compose up -d postgres          # banco de desenvolvimento
cd backend && ./mvnw spring-boot:run   # API em :8080, perfil dev
cd backend && ./mvnw verify            # build + testes (requer Docker para Testcontainers)
cd frontend && npm run dev             # Vite em :5173 com proxy de /api
cd frontend && npm test                # Vitest
cd frontend && npx playwright test     # E2E
```

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
