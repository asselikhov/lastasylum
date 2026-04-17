# SquadRelay Backend

NestJS backend for alliance communication features:
- JWT authentication with alliance roles (R2-R5)
- Access + refresh token sessions
- Realtime chat stream over Socket.IO
- MongoDB persistence for users and messages
- Role-based permissions and moderation controls
- Rate limiting on sensitive endpoints

## Tech stack

- NestJS + TypeScript
- MongoDB (Mongoose)
- JWT (Passport)
- Socket.IO gateway

## Environment variables

Copy `.env.example` to `.env` and fill real values:

```bash
PORT=3000
MONGODB_URI=...
MONGODB_DB_NAME=last_asylum
JWT_SECRET=...
JWT_EXPIRES_IN=7d
JWT_REFRESH_SECRET=...
JWT_REFRESH_EXPIRES_IN=30d
```

## Run locally

```bash
npm install
npm run start:dev
```

## API overview

- `POST /auth/register` - register player
- `POST /auth/login` - login player
- `POST /auth/refresh` - rotate token pair by refresh token
- `POST /auth/logout` - close active refresh session (JWT)
- `GET /users/me` - current profile (JWT)
- `GET /users` - list alliance members (R4+)
- `PATCH /users/role` - change member role (R5)
- `PATCH /users/mute` - mute player for N minutes (R4+)
- `GET /chat/messages` - recent alliance messages (JWT)
- `POST /chat/messages` - create message (JWT) and broadcast `message:new` to the alliance room

## WebSocket overview

Namespace: `/chat`

- `room:join` with `{ allianceId }`
- `message:send` with `{ allianceId?, text }`
- server emits `message:new`
- connect with `auth.token` (Bearer access token)
