# Runbook de produção

Como colocar e manter o sistema numa VPS: preparar o servidor, fazer o primeiro deploy, atualizar, voltar versão, fazer backup e restaurar. As decisões por trás de cada passo estão em `docs/DECISIONS.md` (D-057, D-059, D-087, D-105 a D-112).

Os comandos supõem Ubuntu Server 24.04 LTS, uma VPS de 2 GB (ou 1 GB com swap, ver D-105) e o repositório clonado em `/opt/resort`. Nenhum valor real (domínio, e-mail, senhas, nome do cliente) entra no repositório: eles ficam só no `/opt/resort/.env.prod` do servidor e com o operador.

Atalho usado em todo o documento:

```bash
alias dc='docker compose -f /opt/resort/docker-compose.prod.yml --project-directory /opt/resort --env-file /opt/resort/.env.prod'
```

## 1. O que roda no servidor

| Serviço | Imagem | Função |
|---|---|---|
| `nginx` | `ghcr.io/<dono>/<repositório>/nginx` | Frontend, proxy de `/api`, HTTPS e cabeçalhos (D-107). O único que publica portas: 80 e 443. |
| `backend` | `…/backend` | API Spring Boot, como `resort_app` (só DML). Readiness na porta interna 8081 (D-059). |
| `migrate` | `…/backend` | Migrations e permissões como `resort_owner`; roda e termina antes do backend (D-057). |
| `postgres` | `postgres:16` (fixada por digest) | Banco. Superusuário só pelo socket local; papéis só pela rede interna (D-057). |
| `backup` | `…/backup` | `pg_dump` diário cifrado com age, retenção de 14 dias (D-110). |
| `certbot` | `certbot/certbot` | Renovação do Let's Encrypt a cada 12 h (perfil `letsencrypt`, D-109). |

Volumes: `pgdata` (banco), `backups`, `letsencrypt` (certificados) e `acme-webroot`.

## 2. Preparar a VPS

Faça tudo isto antes de instalar o sistema.

### 2.1 Usuário e SSH só por chave

```bash
adduser deploy && usermod -aG sudo deploy
mkdir -p /home/deploy/.ssh && cp ~/.ssh/authorized_keys /home/deploy/.ssh/   # a chave pública do operador
chown -R deploy:deploy /home/deploy/.ssh && chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
```

Em `/etc/ssh/sshd_config` (ou num arquivo em `/etc/ssh/sshd_config.d/`):

```text
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
```

Depois, `sudo systemctl reload ssh`. **Antes de fechar a sessão atual**, confirme numa segunda janela que `ssh deploy@<servidor>` entra com a chave.

### 2.2 Firewall: só 22, 80 e 443

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
sudo ufw enable && sudo ufw status verbose
```

> **As portas publicadas pelo Docker não passam pelas regras do ufw.** O Docker cria as próprias regras no iptables (tabela nat e cadeias `DOCKER`/`DOCKER-USER`), que valem antes das do ufw. Uma porta publicada no `docker-compose.prod.yml` fica aberta para a internet mesmo que o ufw a bloqueie. Por isso **nenhuma porta além de 80 e 443 pode ser publicada**: PostgreSQL, backend e a porta 8081 não têm `ports`, e o `scripts/prod-check.sh` falha se algum serviço além do Nginx publicar porta. Nunca acrescente `ports` a outro serviço, nem "só para testar".

### 2.3 Atualizações automáticas de segurança

```bash
sudo apt update && sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades   # responder "Sim"
```

O padrão do Ubuntu instala só as atualizações de segurança. Reinícios do kernel ficam a cargo do operador, numa janela combinada.

### 2.4 Docker

Instale o Docker Engine e o plugin compose pelo repositório oficial do Docker (<https://docs.docker.com/engine/install/ubuntu/>), e depois `sudo usermod -aG docker deploy`.

Crie `/etc/docker/daemon.json`:

```json
{
  "userland-proxy": false
}
```

e `sudo systemctl restart docker`. O motivo está na seção 3.

### 2.5 Swap (só na VPS de 1 GB)

```bash
sudo fallocate -l 1G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

No `.env.prod`, use os valores de 1 GB indicados no `.env.prod.example`.

## 3. Portas, IP de origem e IPv6

O backend grava na auditoria e usa no limite de login o IP que o Nginx viu (D-108). Esse IP só é o do cliente se ele chegar preservado ao container:

- Uma porta publicada vira uma regra **DNAT** no iptables: o pacote de um cliente externo chega ao Nginx **com o IP de origem preservado**.
- O **`docker-proxy`** (userland-proxy) atende ao que o DNAT não cobre: conexões feitas do próprio servidor e **IPv6 quando o Docker não tem IPv6 na rede do container**. Nesses casos, o Nginx vê o IP do gateway do Docker (por exemplo, `172.30.0.1`), e a auditoria e o limite de login passam a ver todos os clientes como um só.

A pilha está configurada para preservar o IP:

- as portas são publicadas explicitamente em IPv4 (`HTTP_BIND` e `HTTPS_BIND`, padrão `0.0.0.0`);
- `"userland-proxy": false` no `daemon.json` (seção 2.4) faz o Docker usar só o iptables;
- **o domínio não deve ter registro AAAA**, só A. Com AAAA, clientes IPv6 ou não conectam ou chegam com o IP errado. Só crie AAAA depois de configurar o IPv6 do Docker com `ip6tables` e conferir a seção 7 também por IPv6.

## 4. Imagens e acesso ao GHCR

As imagens são geradas pelo CI quando uma tag de versão `vX.Y.Z` é criada, e só se os checks passarem (D-112). Cada uma tem duas tags: a da versão e `sha-<commit>`.

Com o repositório privado, as imagens também são privadas. O servidor precisa de um token **só de leitura**:

1. No GitHub, crie um token (classic) com o escopo **`read:packages`** e nada mais, com validade definida. Guarde-o no gerenciador de senhas.
2. No servidor, como `deploy`:

   ```bash
   echo '<token>' | docker login ghcr.io -u <usuário-do-github> --password-stdin
   ```

   O Docker guarda a credencial em `~/.docker/config.json`. Quando o token vencer, gere outro e repita o login.

## 5. Arquivos no servidor

O servidor precisa de `docker-compose.prod.yml`, `postgres/` e `scripts/`. A forma simples é clonar o repositório na tag da versão:

```bash
sudo mkdir -p /opt/resort && sudo chown deploy:deploy /opt/resort
git clone --branch vX.Y.Z --depth 1 git@github.com:<dono>/<repositório>.git /opt/resort
```

Com o repositório privado, use uma **deploy key só de leitura**: gere um par de chaves SSH no servidor e cadastre a pública em *Settings → Deploy keys* do repositório, sem marcar "Allow write access".

## 6. Chave do backup (no computador do operador)

O backup é cifrado para uma chave pública. **A chave privada nunca fica no servidor**, assim quem invadir a VPS não lê os backups (D-110).

```bash
age-keygen -o resort-backup.key   # no computador do operador; instale o age (https://age-encryption.org)
```

- A linha `# public key: age1…` vai para `BACKUP_AGE_RECIPIENT` no `.env.prod`.
- O arquivo `resort-backup.key` (a chave privada) vai para o gerenciador de senhas **e** para uma cópia offline (por exemplo, pen drive guardado). Sem ela, nenhum backup pode ser restaurado.

## 7. Primeiro deploy

1. **DNS:** registro **A** do domínio para o IP da VPS. Sem AAAA (seção 3).
2. **`.env.prod`:**

   ```bash
   cp /opt/resort/.env.prod.example /opt/resort/.env.prod && chmod 600 /opt/resort/.env.prod
   ```

   - Preencha tudo. Gere senhas com `openssl rand -base64 32`, uma diferente para cada variável.
   - `BACKEND_IMAGE`, `NGINX_IMAGE` e `BACKUP_IMAGE` recebem a tag da versão.
   - Comece com `LETSENCRYPT_STAGING=true`.
3. **Subir:**

   ```bash
   dc pull && dc up -d
   dc ps   # todos "healthy"; o migrate "exited (0)"
   ```

   Nesta primeira subida, o Nginx usa um certificado provisório (D-109).
4. **Certificado de teste** (ambiente de staging do Let's Encrypt, sem limite de emissões):

   ```bash
   /opt/resort/scripts/issue-cert.sh /opt/resort/.env.prod
   ```

   Se der certo, o navegador mostra um certificado "(STAGING)". Ele não é confiável, mas prova que o desafio funcionou.
5. **Certificado real:**
   - apague o de teste: `dc --profile letsencrypt run --rm certbot delete --cert-name <domínio>`;
   - troque para `LETSENCRYPT_STAGING=false` no `.env.prod`;
   - rode de novo o `issue-cert.sh`.

   A renovação automática fica a cargo do serviço `certbot` (`COMPOSE_PROFILES=letsencrypt`).
6. **Primeiro acesso:** entre com `APP_BOOTSTRAP_ADMIN_EMAIL` e a senha inicial, e troque a senha (é obrigatório). A partir daí, as variáveis do ADMIN inicial são ignoradas (D-052).
7. Faça as **verificações pós-deploy** (seção 8).

## 8. Verificações pós-deploy

Faça todas depois do primeiro deploy e depois de cada mudança de infraestrutura.

1. **Pilha:**
   - `dc ps`: todos `healthy`;
   - `curl -s https://<domínio>/actuator/health` responde exatamente `{"status":"UP"}`.
2. **Cabeçalhos:** `curl -sI https://<domínio>/` mostra `strict-transport-security`, `content-security-policy`, `permissions-policy` com `camera=(self)`, `x-content-type-options`, `referrer-policy` e `x-frame-options`.
3. **HTTP para HTTPS:** `curl -sI http://<domínio>/leads` responde `301` para `https://`.
4. **Portas:**
   - de fora do servidor, `nmap -Pn <ip>` (ou um verificador online) mostra só 22, 80 e 443;
   - `sudo ss -ltnp | grep docker-proxy` não mostra nada (userland-proxy desligado).
5. **IP de origem:**
   - `dc logs --tail 20 nginx` mostra IPs públicos, não `172.x`;
   - **faça login a partir de um celular fora da rede do servidor (no 4G/5G, com o Wi-Fi desligado)**, e na tela de Auditoria, na linha `LOGIN` desse acesso, confira que o IP é o IP público do celular. Veja o IP do celular num site como "qual é meu ip" no próprio celular.
   - Se aparecer `172.x`, revise a seção 3 (`userland-proxy`, AAAA).
6. **Câmera:** no mesmo celular, com o perfil de Portaria, abra "Validar Convite" e leia o QR de um convite de teste. É a pendência da D-093; só funciona com HTTPS.
7. **Backup:** `dc exec backup backup.sh` faz um backup na hora. Confira com `dc exec backup ls -l /backups` e faça a cópia para fora do servidor (seção 10).

## 9. Atualização de versão

1. Leia as notas da versão. Se ela traz migration, a volta atrás exige restaurar o backup (seção 11).
2. **Backup antes:** `dc exec backup backup.sh`, e copie-o para fora do servidor (seção 10).
3. Atualize os arquivos e as tags:

   ```bash
   cd /opt/resort && git fetch --tags && git checkout vX.Y.Z
   # no .env.prod: BACKEND_IMAGE, NGINX_IMAGE e BACKUP_IMAGE com :vX.Y.Z
   dc pull && dc up -d
   ```

   O `up -d` recria o `migrate` com a imagem nova (migrations e permissões), depois o backend (que só fica pronto após o readiness) e o Nginx.
4. **Verificações pós-deploy:** seção 8, itens 1, 2 e 5.

O `scripts/prod-check.sh` ensaia esta atualização e a volta na pilha local.

## 10. Backup e cópia fora da VPS (obrigatória)

- **Automático:** todos os dias às `BACKUP_TIME` (padrão 03:00) no fuso da operação.
  - O arquivo é `resort-AAAAMMDDTHHMMSSZ.dump.age`, no volume `backups`: `pg_dump` como `resort_backup` (só leitura), sem os dados das sessões, cifrado para `BACKUP_AGE_RECIPIENT`.
  - Arquivos com 14 dias ou mais são apagados.
- **A cópia na própria VPS não basta.** Se o servidor, o disco ou a conta no provedor forem perdidos, ou se houver ransomware, o banco e os backups se vão juntos.
- **A cópia fora da VPS é obrigatória na 11b.** O destino exato será decidido na 11b.
- **Modelo recomendado:** o operador **puxa** os arquivos. Assim, o servidor não guarda credencial de nenhum destino externo.
  - No servidor, um usuário só para isso, com uma chave SSH restrita ao `rrsync` em modo leitura (em `authorized_keys`):

    ```text
    command="rrsync -ro /var/lib/docker/volumes/resort_backups/_data",restrict ssh-ed25519 AAAA… copia-backup
    ```

    O usuário precisa de leitura nessa pasta (grupo ou ACL).
  - No destino, todo dia depois do horário do backup:

    ```bash
    rsync -av -e "ssh -i ~/.ssh/copia-backup" copia@<servidor>: /caminho/do/destino/resort/
    ```
- Os arquivos são cifrados; o destino não precisa ser de confiança para ler, só para guardar. Mantenha lá uma retenção maior que a do servidor (por exemplo, 90 dias).
- **Snapshots do provedor** são um complemento, não um substituto: ficam na mesma conta.
- **Teste a restauração** pelo menos a cada três meses, numa máquina à parte (seção 11). Backup que nunca foi restaurado não conta.

## 11. Restauração e volta de versão

### 11.1 Restaurar um backup

1. Copie a chave privada para o servidor **só durante a restauração**, na memória:

   ```bash
   install -m 600 /dev/null /dev/shm/resort-backup.key   # e cole o conteúdo da chave nele
   ```
2. Com o arquivo do backup no servidor (da pasta `backups` ou da cópia externa):

   ```bash
   /opt/resort/scripts/restore.sh /opt/resort/.env.prod /caminho/resort-….dump.age /dev/shm/resort-backup.key
   shred -u /dev/shm/resort-backup.key
   ```

   O script:
   - confere que a chave decifra o backup; se não decifrar, para sem mexer em nada;
   - para o Nginx, o backend e o backup;
   - recria o banco e restaura como `resort_owner`;
   - roda o `migrate`, que aplica as migrations mais novas e as permissões de `resort_app`;
   - sobe a pilha.
3. Faça as verificações da seção 8. Tudo o que foi gravado depois do backup se perde.

### 11.2 VPS nova (perda total)

Siga as seções 2 a 7 até o `.env.prod`, **com as mesmas senhas de banco** do servidor perdido (elas estão no gerenciador de senhas). Depois:

```bash
dc pull && dc up -d postgres   # cria os papéis na criação do volume
```

Em seguida, a seção 11.1 com o backup da cópia externa, e depois o certificado (seção 7, passo 5). O `scripts/prod-check.sh` ensaia exatamente essa perda total: apaga tudo e restaura a partir da cópia.

### 11.3 Voltar para a versão anterior

- **Sem migration na versão nova:** volte as tags no `.env.prod` (e o `git checkout` da versão anterior) e rode `dc up -d`.
- **Com migration:** as migrations só andam para a frente. A versão anterior pode não funcionar com o banco novo. Restaure o backup feito antes da atualização (seção 9, passo 2) e volte as tags.

## 12. Logs

- **Onde:** `dc logs <serviço>` (saída padrão dos containers).
- **Retenção:** `json-file` com 10 MB × 5 arquivos por serviço, no máximo cerca de 50 MB cada. No volume esperado, isso dá algumas semanas.
- **O que não aparece** (D-111):
  - o log de acesso do Nginx grava método, caminho sem query, status, tamanho e tempo, sem Referer nem User-Agent, porque a query de `/api/leads` pode levar CPF;
  - o backend não registra corpo de requisição, CPF, senha nem código de convite (regra 5 do CLAUDE.md, D-069).
- **Risco residual tratado:** quando o backend não responde, o log de erro do Nginx gravaria a linha do pedido **com a query**. Em `/api/`, o log de erro fica só no nível `crit`, e a falha aparece como status 502/504 no log de acesso. Para investigar uma queda, olhe `dc ps` e `dc logs backend`.

## 13. Manutenção de rotina

- **Semanal:**
  - `dc ps`: todos saudáveis;
  - a pasta de backups tem um arquivo por dia;
  - a cópia externa está em dia.
- **Mensal:** `docker system df` para ver o espaço em disco; apague imagens antigas com `docker image prune`, sem `-a`, para manter a versão anterior disponível para volta.
- **Certificado:** renovação automática pelo `certbot`, antes do vencimento. Confira a validade de vez em quando com `curl -vI https://<domínio> 2>&1 | grep expire`.
