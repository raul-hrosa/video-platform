# Video Platform

Plataforma de videochamada em tempo real, no estilo Google Meet: salas com
link compartilhável, convidados sem cadastro, compartilhamento de tela,
indicadores de mic/câmera e sinal de conexão, histórico e analytics por sala,
e agendamentos recorrentes com link permanente.

A camada de mídia (WebRTC) é abstraída atrás de um contrato próprio, então o
provedor por trás pode trocar sem tocar no resto do sistema. Hoje o único
provedor é o **LiveKit Cloud** (gerenciado); a escolha é uma variável de
ambiente (`MEDIA_PROVIDER`), e o restante da aplicação (backend e frontend)
fala só com a interface da plataforma, nunca diretamente com o SDK do
provedor — plugar outro provider no futuro é implementar esse contrato, sem
mudar os consumidores.

## Arquitetura

```
React (Vite + TS + Tailwind)
   |
   |  HTTP / REST   (nginx faz proxy de /api -> backend)
   v
Spring Boot API  ---- emite o token de mídia (LiveKit)
   |          \
   v           \--- recebe webhooks do provedor (ciclo de vida da sala)
PostgreSQL          ^
   ^                |
   |          Provedor de mídia ---- WebRTC ----  participante A / B / ...
   \--- rooms, participants, sessions, webhook_events, appointments
```

- **React**: interface da chamada, seleção de câmera/microfone, preview,
  compartilhamento de tela, indicador de qualidade de conexão, console de
  salas e histórico.
- **Spring Boot**: ciclo de vida de salas e agendamentos, emissão de token de
  mídia, recebimento e processamento de webhooks, analytics, autenticação,
  multi-tenant. As credenciais do provedor de mídia nunca saem do backend.
- **PostgreSQL**: salas, participantes, sessões, eventos de webhook,
  agendamentos (migrations Flyway).
- **LiveKit**: infraestrutura de mídia/WebRTC e origem dos webhooks de ciclo
  de vida.

Monólito modular no backend, pacotes por feature: `organization`,
`roomprofile`, `room`, `appointment`, `participant`, `webhook`, `analytics`,
`quality`, `provider` (contratos de mídia + implementação LiveKit — o
desenho existe para permitir outro adapter no futuro), `auth`, `common`. Sem
microsserviços.

O limite de isolamento dos dados é a **Organization** (ver seção
"Organizations"), não o usuário individual.

## Pré-requisitos

- Docker + Docker Compose
- Uma conta no [LiveKit Cloud](https://cloud.livekit.io)

## Configuração

1. Copie o arquivo de exemplo e preencha as credenciais:

   ```bash
   cp .env.example .env
   ```

2. Preencha as credenciais do LiveKit Cloud (`MEDIA_PROVIDER=livekit` já é o
   padrão e precisa bater com `VITE_MEDIA_PROVIDER`):

   ```env
   # LiveKit Cloud (Settings -> Keys no painel)
   LIVEKIT_URL=wss://SEU-PROJETO.livekit.cloud
   LIVEKIT_API_KEY=APIxxxxxxxxxxxx
   LIVEKIT_API_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
   VITE_LIVEKIT_URL=wss://SEU-PROJETO.livekit.cloud
   ```

3. Gere um segredo para assinar o token de sessão da plataforma (mínimo 32
   bytes) e coloque em `JWT_SECRET`:

   ```bash
   openssl rand -base64 48
   # ou: node -e "console.log(require('crypto').randomBytes(48).toString('base64'))"
   ```

   Os demais valores (Postgres, portas, `JWT_EXPIRATION_SECONDS=3600`,
   `ROOM_EXPIRATION_CHECK_INTERVAL=PT30S`) já têm padrão.

O `.env` está no `.gitignore` e não deve ser commitado.

## Execução

```bash
docker compose up --build
```

## Acesso

| Serviço   | URL                                   |
|-----------|----------------------------------------|
| Frontend  | http://localhost:8080                 |
| Backend   | http://localhost:8081                 |
| Health    | http://localhost:8081/actuator/health |
| Postgres  | localhost:5432 (`video` / `video`)    |

As portas podem ser mudadas no `.env` (`FRONTEND_PORT`, `BACKEND_PORT`,
`POSTGRES_PORT`).

## API

Base: `/api/v1`. Salvo os públicos abaixo, **toda rota exige**
`Authorization: Bearer <platform-jwt>`.

### Público

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/auth/register` | Cadastro. Body `{ name, email, password }` (senha >= 8). `201 { id, name, email }`. |
| `POST` | `/auth/login` | Login. Body `{ email, password }`. `200 { accessToken, tokenType: "Bearer", expiresIn }`. |
| `GET`  | `/auth/me` | (autenticado) `{ id, name, email }` do usuário do token. |
| `POST` | `/rooms/{roomId}/guest-token` | Entrar como convidado, sem conta. Body `{ name }` (1–60 chars). Identidade gerada no backend. `404` sala inexistente, `409 ROOM_EXPIRED`. |
| `GET`  | `/rooms/{roomId}/participant-names` | Nomes de exibição por identidade — o provedor de mídia não propaga nome de terceiros, então o cliente resolve por aqui. |
| `GET`  | `/public/appointments/{publicAccessId}` | Link permanente do agendamento. Payload mínimo: `{ title, participantName, state, scheduledStart, scheduledEnd, joinWindowOpensAt, roomId, nextOccurrenceStart }`. `state` = `BEFORE_WINDOW`\|`WAITING_ROOM`\|`JOINABLE`\|`ENDED`\|`CANCELLED`. |
| `POST` | `/public/appointments/{publicAccessId}/token` | Entra na ocorrência atual. Body `{ name }` opcional. `409` fora da janela de entrada. |
| `POST` | `/webhooks/livekit` | Webhook do LiveKit — autenticação própria (assinatura), não JWT de usuário. |
| `GET`  | `/actuator/health` | Healthcheck. |

### Room Profiles — configuração reutilizável (opcional)

Um atalho para quem cria o mesmo tipo de sala com frequência. Não é
obrigatório: ver "Salas" abaixo para a criação direta.

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/room-profiles` | Cria perfil. Body `{ name, durationMinutes, type }` (`durationMinutes` 1–480; `type` = `LESSON`\|`CONSULTATION`\|`MEETING`\|`INTERVIEW`\|`OTHER`). `201`. |
| `GET` | `/room-profiles` | Perfis ativos da Organization. |
| `GET` | `/room-profiles/{profileId}` | Detalhe. |
| `PUT` | `/room-profiles/{profileId}` | Atualiza. Não altera salas já criadas a partir dele (elas guardam um snapshot). |
| `DELETE` | `/room-profiles/{profileId}` | Soft delete — some das listagens, salas antigas ficam. `204`. |
| `POST` | `/room-profiles/{profileId}/rooms` | Cria uma sala a partir do perfil (copia nome/duração, calcula expiração). `201`. |
| `GET` | `/room-profiles/{profileId}/summary` | `{ roomsTotal, totalCallSeconds, participations }`. |

### Salas

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/rooms` | Cria uma sala direta — um clique, sem nome nem duração fixa. `201`. |
| `GET`  | `/rooms?page&size&status&roomProfileId&createdFrom&createdTo` | Histórico paginado da Organization. Ordenação padrão `createdAt DESC`. |
| `GET`  | `/rooms/summary?todayStart&weekStart` | Panorama: `{ roomsToday, roomsThisWeek, totalRooms, totalCallSeconds, distinctParticipants }`. |
| `GET`  | `/rooms/{roomId}` | Detalhe. |
| `GET`  | `/rooms/{roomId}/participants` | Participantes da sala com totais agregados. |
| `GET`  | `/rooms/{roomId}/participants/{participantRef}` | Analytics detalhado de um participante. |
| `GET`  | `/rooms/{roomId}/participants/summary` | Agregado: `{ distinctParticipants, totalSessions, totalEntries, totalParticipantSeconds, participants:[...] }`. |
| `GET`  | `/rooms/{roomId}/events` | Linha do tempo de eventos da sala. |
| `GET`  | `/rooms/{roomId}/sessions` | Sessões de participação. |
| `GET`  | `/rooms/{roomId}/analytics` | Métricas da sala + bloco `quality`. |
| `GET`  | `/rooms/{roomId}/quality` | Qualidade por participante (última leitura de cada um). |
| `POST` | `/rooms/{roomId}/token` | Token de mídia para o usuário autenticado. Identidade estável derivada do usuário. `404`/`409 ROOM_EXPIRED`. |
| `GET`  | `/rooms/{roomId}/sessions/mine` | A própria sessão aberta do usuário na sala. |

### Agendamentos

Um `Appointment` é o compromisso recorrente e o link permanente
(`/r/{publicAccessId}`). Cada realização vira uma `AppointmentOccurrence` com a
sua própria sala — sessões, qualidade e analytics ficam separados por
ocorrência, no histórico normal de salas.

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/appointments` | Cria. Body `{ roomProfileId, title, participantName?, startsAt, timezone, recurrence? }`; `recurrence` = `{ type: NONE\|WEEKLY, dayOfWeek?, until? }`. `201`. |
| `GET`  | `/appointments` | Agendamentos da Organization, ordenados pela próxima ocorrência. |
| `GET`  | `/appointments/{id}` | Detalhe + próxima ocorrência + link de entrada. |
| `GET`  | `/appointments/{id}/occurrences` | Histórico por ocorrência. |
| `PUT`  | `/appointments/{id}` | Atualiza agenda/recorrência. Não altera ocorrências já realizadas. |
| `POST` | `/appointments/{id}/cancel` | Cancela; ocorrências futuras não iniciadas também. `204`. |

### Organizations

| Método | Rota | Descrição |
|--------|------|-----------|
| `GET` | `/organizations/current` | `{ id, name, slug, role }` da Organization do usuário. |
| `PUT` | `/organizations/current` | Renomeia. Só o dono da Organization. |
| `GET` | `/organizations/current/members` | Lista de membros com role. |

Erros seguem `{ "code": "...", "message": "..." }`, nunca stack trace.

## Autenticação

- **Dois tokens distintos, nunca misturados**: o token de sessão da
  plataforma (assinado com `JWT_SECRET`) autentica o usuário na API; o token
  de mídia (LiveKit) só serve para conectar na chamada e é gerado no backend
  a partir da identidade autenticada. O token da plataforma nunca é enviado
  ao provedor de mídia.
- Senha: BCrypt. Nunca aparece em resposta nem em log. Login usa mensagem
  genérica — não revela se o e-mail existe.
- O acesso de gestão (histórico, participantes, analytics, qualidade, CRUD de
  perfis e agendamentos) é de qualquer membro da Organization dona,
  conforme a role. Recurso de outra Organization retorna `404`.
- **Entrar na chamada** só precisa de um `roomId` válido e não expirado:
  - **com conta** — identidade estável ligada ao usuário;
  - **como convidado** — informa um nome, sem cadastro.
  Compartilhar uma sala é passar o link, estilo Google Meet. Ter o link
  significa "tentar entrar", nunca "ser dono".
- No frontend o token fica em `sessionStorage` — sobrevive a refresh, some ao
  fechar a aba. Um `401` da API limpa a sessão e volta ao login.

## Organizations (multi-tenant)

```
Organization
 ├── Members  (User + role: OWNER | ADMIN | MEMBER)
 ├── Room Profiles
 │     └── Rooms
 │          ├── Participants / Sessions
 │          ├── Quality
 │          └── Analytics
 └── Appointments
```

Toda `Room` e `RoomProfile` pertence a exatamente uma Organization; uma
tentativa de acesso cruzado recebe `404` (nunca vaza nome, participantes ou
métricas). Ao criar a conta, o usuário ganha automaticamente uma Organization
pessoal e vira `OWNER` dela.

| Operação | OWNER | ADMIN | MEMBER |
| -------- | :---: | :---: | :----: |
| Ver Organization | ✅ | ✅ | ✅ |
| Ver / gerenciar membros | ✅ | ✅ | ❌ |
| Criar sala / perfil / agendamento | ✅ | ✅ | ✅ |
| Editar perfil | ✅ | ✅ | só o criador |
| Excluir perfil | ✅ | ✅ | ❌ |
| Ver histórico / analytics | ✅ | ✅ | ✅ |
| Renomear a Organization | ✅ | ❌ | ❌ |

Não há convite por e-mail, switcher de Organization nem múltiplas
Organizations na interface por enquanto.

## Salas — criação e ciclo de vida

Existem dois jeitos de criar uma sala:

- **Direta** (`POST /rooms`) — um clique, sem nome nem duração fixa. Não
  expira sozinha.
- **A partir de um Room Profile** (`POST /room-profiles/{id}/rooms`) — copia
  nome e duração do perfil (um snapshot: editar o perfil depois não muda a
  sala já criada) e calcula `expires_at`.

```
sala criada -> WAITING
1o participante entra   -> ACTIVE   (started_at)
ultimo participante sai -> ENDED    (ended_at)
expires_at atingido     -> EXPIRED  (quando a sala tem duração)
```

- Ao atingir `expires_at`, a sala para de aceitar novos participantes
  (`409 ROOM_EXPIRED`), mas quem já está conectado não é derrubado.
- A checagem de expiração acontece tanto na emissão do token (leitura, sem
  escrever nada) quanto numa varredura periódica
  (`ROOM_EXPIRATION_CHECK_INTERVAL`) que só mantém o estado do banco e os
  logs em dia.
- `ENDED` e `EXPIRED` são estados finais.

### Link compartilhável

`/room/{roomId}`. O frontend lê a URL no carregamento:

- **logado** → vai direto ao setup de dispositivos e pede o token
  autenticado;
- **deslogado** → pede um nome e entra como convidado.

## Banco de dados

Migrations Flyway em `backend/src/main/resources/db/migration`, cobrindo:
salas e sessões de participação; qualidade de conexão; usuários e
autenticação; room profiles; Organizations e membership (com backfill para
contas existentes); agendamentos recorrentes e suas ocorrências; e o
desacoplamento entre sala e perfil (sala passa a poder existir sem perfil).

Timestamps são persistidos em UTC (`Instant` no Java, `timestamptz` no
Postgres).

## Histórico e analytics

Transforma os dados já coletados (salas, sessões, qualidade, reconexões) numa
visão operacional. Nada é inventado — quando o dado não existe, a API
devolve `null`/vazio e a interface diz "sem dados".

- **Histórico** (`GET /rooms`) — paginado, filtrável por status, perfil e
  intervalo de criação. Isolado por Organization.
- **Analytics** (`GET /rooms/{roomId}/analytics`) — duração, participantes
  distintos, pico de participantes simultâneos, participante-segundos totais
  e agregados de qualidade.
- **Sessões** (`GET /rooms/{roomId}/sessions` e `.../participants/summary`) —
  uma pessoa pode ter várias sessões (saiu e voltou); participantes distintos
  são contados separadamente de entradas.
- **Qualidade** (`GET /rooms/{roomId}/quality`) — última leitura de cada
  participante; quem não enviou métrica aparece com os campos em `null`, não
  um valor inventado.
- **Dashboard** (`GET /rooms/summary`, `GET /room-profiles/{id}/summary`).

## Webhooks

O backend valida a autenticidade de cada webhook antes de processar, com o
`WebhookReceiver` do SDK oficial do LiveKit (JWT + checksum do corpo).
Reenvios são ignorados por `event_id` (idempotência). Eventos tratados:
início/fim de sala, entrada/saída de participante.

Sem o webhook configurado a chamada de vídeo funciona normalmente, mas as
transições de estado da sala e as sessões de participantes não são
registradas.

### Em desenvolvimento

O provedor de mídia precisa alcançar o backend por uma URL pública. Use um
túnel:

```bash
ngrok http 8081
```

E configure a URL do webhook no painel do LiveKit (`.../api/v1/webhooks/livekit`).

## Logs e observabilidade

O backend produz logs estruturados para stdout, com o objetivo de
reconstruir o que aconteceu numa chamada a partir deles.

- Perfil `docker`/`prod`: JSON (encoder logstash).
- Perfil de desenvolvimento local: texto legível.
- Toda requisição recebe um `correlationId` (header `X-Correlation-ID`,
  gerado se o cliente não enviar) que aparece em todos os logs daquela
  requisição.
- Principais grupos de evento: HTTP, sala, token, participante, webhook,
  qualidade de conexão, screen share.
- Nunca é logado: segredo de assinatura, chaves de API completas, token do
  provedor de mídia, o header `Authorization`, assinatura de webhook,
  cookies, senhas.

```bash
docker compose logs -f backend
docker compose logs backend | grep '"roomId":"room-abc123"'
```

O frontend tem um logger próprio (`frontend/src/services/logger.ts`), sem
`console.log` espalhado, que registra localmente eventos de
permissão/dispositivo/conexão. Não envia nada para o backend nesta fase.

## Qualidade de conexão

Indicador de qualidade por participante, em linguagem simples (sinal tipo
celular) em vez de números crus — o detalhe técnico fica num painel de
diagnóstico opcional.

- O nível vem do evento de qualidade de conexão do próprio SDK do LiveKit,
  calculado no servidor.
- Métrica indisponível no navegador aparece como ausente, nunca como zero.
- O indicador aplica uma pequena histerese na apresentação para não piscar
  entre dois níveis em segundos — sem alterar a classificação oficial.

A leitura persistida (para histórico/analytics) é feita a cada poucos
segundos e fica em `connection_quality_metrics`, ligada à sessão de
participação.

## Na chamada

- **Câmera e microfone**: seleção de dispositivo antes de entrar, alternância
  durante a chamada.
- **Compartilhamento de tela**: uma faixa de mídia separada da câmera — a
  câmera continua ligada enquanto a tela é compartilhada. Suporta áudio da
  aba/sistema quando o navegador oferece. Com duas ou mais pessoas
  compartilhando ao mesmo tempo, uma fica em destaque e as outras aparecem
  como miniaturas; dá para fixar qual delas fica em destaque.
- **Indicadores de mic e câmera**: mostrados por participante, compartilhados
  pela sala (não é um estado só local).
- **Medidor de volume**: nível de áudio por participante e pela tela
  compartilhada, calculado no navegador a partir do stream recebido.
- **Câmera desligada**: mostra o nome da pessoa em vez de um quadro preto.

## Testando uma chamada

Precisa de dois participantes (um por navegador). O dono da sala tem conta; o
segundo pode entrar como convidado pelo link, sem cadastro.

1. **Navegador 1**: http://localhost:8080 → crie uma conta → login.
2. Crie uma sala (direta ou a partir de um perfil) → copie o link.
3. Libere câmera/microfone, escolha os dispositivos, entre.
4. **Navegador 2** (janela anônima ou outro navegador): abra o link sem fazer
   login → informe um nome → entre como convidado.
5. Os dois devem se ver e se ouvir. Teste microfone, câmera, compartilhamento
   de tela, saída.
6. Com o webhook configurado, o dono da sala consulta depois o histórico e a
   analytics; o convidado não tem acesso a isso.

> Câmera/microfone exigem contexto seguro. `localhost` funciona; de outra
> máquina, use HTTPS.

## Desenvolvimento local (sem Docker)

Backend (precisa de Postgres em `localhost:5432`):

```bash
cd backend
./mvnw spring-boot:run
./mvnw test
```

Frontend:

```bash
cd frontend
cp .env.example .env          # preencha VITE_LIVEKIT_URL e VITE_API_URL
npm install
npm run dev                   # http://localhost:5173
npm test                      # Vitest
```

O dev server (`vite`) já faz proxy de `/api` para `localhost:8081`, então com
`VITE_API_URL=/api` as chamadas ficam same-origin.

### Expondo por um túnel (ngrok)

- **Stack Docker**: `ngrok http 8080` — o nginx já serve front e API na
  mesma origem.
- **Front via `npm run dev`**: `ngrok http 5173`. O CORS já vem liberado
  para qualquer origem por padrão (a API não usa cookies); para restringir,
  defina `APP_CORS_ALLOWED_ORIGINS` com uma lista de origens.

## Testes

Backend: `cd backend && ./mvnw test`. Cobre emissão de token (autenticado e
convidado), room profiles (CRUD, soft delete, ownership), salas (criação
direta e a partir de perfil, expiração, paginação), participantes e sessões,
agendamentos e ocorrências, webhooks (ciclo completo, idempotência, sem
vazar credenciais), analytics, qualidade de conexão, autenticação e
Organizations/multi-tenant. Alguns testes de migração/isolamento usam
Testcontainers e são pulados sem Docker disponível.

Frontend: `cd frontend && npm test` (Vitest). Cobre autenticação,
Organizations, room profiles, fluxo de convidado, histórico e a fronteira
com o SDK do LiveKit.

## Arquitetura futura

```
Organization -> Users -> Rooms -> Participants -> Sessions -> Analytics
                                -> Webhooks -> Public API -> SDK (JS / React / Mobile)
```

- Troca de provedor de mídia sem mudança de código, apenas configuração —
  o contrato já existe (ver `provider/`); hoje só o LiveKit implementa,
  mas abre espaço para outros.

## Fora de escopo (por enquanto)

OAuth / login social, MFA, refresh tokens, API keys públicas, convites por
e-mail, switcher/múltiplas Organizations na interface, RBAC avançado,
cobrança, SDK público, gravação, transcrição, chat, notificações, fila de
mensagens, Kubernetes, dashboard administrativo, rate limiting, sala de
espera / aprovação de convidado.
