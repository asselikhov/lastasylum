import { Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { WsException } from '@nestjs/websockets';
import { Socket } from 'socket.io';
import { AllianceRole } from './enums/alliance-role.enum';
import { normalizeAllianceRole } from './alliance-role.util';

export type SocketGatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

export type SocketWithUser = Socket & {
  data: {
    user?: SocketGatewayUser;
  };
};

type JwtSocketPayload = {
  sub: string;
  username?: string;
  role?: string | AllianceRole;
};

export function extractSocketBearerToken(client: Socket): string | undefined {
  const authPayload = client.handshake.auth as { token?: unknown };
  const authTokenValue = authPayload.token;
  const headerValue = client.handshake.headers.authorization;
  const headerToken =
    typeof headerValue === 'string'
      ? headerValue.replace(/^Bearer\s+/i, '')
      : undefined;
  const rawToken =
    typeof authTokenValue === 'string' ? authTokenValue : headerToken;
  return rawToken?.trim() || undefined;
}

export function verifySocketToken(
  jwtService: JwtService,
  configService: ConfigService,
  rawToken: string,
): SocketGatewayUser {
  let payload: JwtSocketPayload & { sub: string };
  try {
    payload = jwtService.verify<JwtSocketPayload & { sub: string }>(rawToken, {
      secret: configService.getOrThrow<string>('JWT_SECRET'),
    });
  } catch {
    throw new WsException('Invalid websocket token');
  }
  return {
    userId: payload.sub,
    username: (payload.username ?? '').toString(),
    role: normalizeAllianceRole(payload.role),
  };
}

export function attachSocketUser(
  client: SocketWithUser,
  user: SocketGatewayUser,
): void {
  client.data.user = user;
}

export function authenticateSocketConnection(
  client: SocketWithUser,
  jwtService: JwtService,
  configService: ConfigService,
): SocketGatewayUser {
  const rawToken = extractSocketBearerToken(client);
  if (!rawToken) {
    throw new WsException('Missing websocket token');
  }
  const user = verifySocketToken(jwtService, configService, rawToken);
  attachSocketUser(client, user);
  return user;
}

function socketAuthFailureReason(err: unknown): string {
  if (err instanceof WsException) {
    const payload = err.getError();
    if (typeof payload === 'string') return payload;
    if (payload && typeof payload === 'object' && 'message' in payload) {
      const msg = (payload as { message?: unknown }).message;
      if (typeof msg === 'string') return msg;
    }
  }
  if (err instanceof Error) return err.message;
  return 'websocket_auth_failed';
}

/**
 * Auth on socket connect — disconnects client on failure instead of throwing.
 * Uncaught WsException in handleConnection crashes the Node process on Render.
 */
export function authenticateSocketConnectionOrDisconnect(
  client: SocketWithUser,
  jwtService: JwtService,
  configService: ConfigService,
  logger?: Logger,
): SocketGatewayUser | null {
  try {
    return authenticateSocketConnection(client, jwtService, configService);
  } catch (err) {
    const reason = socketAuthFailureReason(err);
    logger?.debug?.(`Rejecting socket id=${client.id}: ${reason}`);
    void client.disconnect(true);
    return null;
  }
}
