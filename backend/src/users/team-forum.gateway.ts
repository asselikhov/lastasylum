import {
  ConnectedSocket,
  MessageBody,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
  WsException,
} from '@nestjs/websockets';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Server, Socket } from 'socket.io';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { TeamForumService, TeamForumMessageRow } from './team-forum.service';

const WS_MESSAGE_WINDOW_MS = 10_000;
const WS_MESSAGE_MAX = 8;

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type SocketData = {
  user?: GatewayUser;
  lastForumRoom?: string;
};

type AuthSocket = Socket<
  Record<string, never>,
  Record<string, never>,
  Record<string, never>,
  SocketData
>;

@WebSocketGateway({
  namespace: '/team-forum',
  cors: {
    origin: parseAllowedOriginsFromEnv(process.env.ALLOWED_ORIGINS) ?? true,
  },
})
export class TeamForumGateway {
  @WebSocketServer()
  server: Server;

  private readonly wsMessageSendTimestamps = new Map<string, number[]>();

  constructor(
    private readonly forumService: TeamForumService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  handleConnection(client: AuthSocket) {
    const authPayload = client.handshake.auth as { token?: unknown };
    const authTokenValue = authPayload.token;
    const headerValue = client.handshake.headers.authorization;
    const headerToken =
      typeof headerValue === 'string'
        ? headerValue.replace(/^Bearer\s+/i, '')
        : undefined;
    const rawToken =
      typeof authTokenValue === 'string' ? authTokenValue : headerToken;

    if (!rawToken) {
      throw new WsException('Missing websocket token');
    }

    let payload: GatewayUser & { sub: string };
    try {
      payload = this.jwtService.verify<GatewayUser & { sub: string }>(
        rawToken,
        {
          secret: this.configService.getOrThrow<string>('JWT_SECRET'),
        },
      );
    } catch {
      throw new WsException('Invalid websocket token');
    }
    client.data.user = {
      userId: payload.sub,
      username: payload.username,
      role: payload.role,
    } satisfies GatewayUser;
  }

  private roomKey(teamId: string, topicId: string): string {
    return `forum:${teamId}:${topicId}`;
  }

  @SubscribeMessage('topic:join')
  async joinTopic(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: { teamId?: string; topicId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const teamId =
      typeof payload?.teamId === 'string' ? payload.teamId.trim() : '';
    const topicId =
      typeof payload?.topicId === 'string' ? payload.topicId.trim() : '';
    if (!teamId || !topicId) {
      throw new WsException('teamId and topicId are required');
    }

    await this.forumService.listMessages(
      teamId,
      topicId,
      client.data.user.userId,
      undefined,
      1,
    );

    const key = this.roomKey(teamId, topicId);
    if (client.data.lastForumRoom && client.data.lastForumRoom !== key) {
      void client.leave(client.data.lastForumRoom);
    }
    void client.join(key);
    client.data.lastForumRoom = key;

    return { event: 'topic:joined', data: { teamId, topicId } };
  }

  @SubscribeMessage('message:send')
  async handleMessage(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    payload: {
      teamId?: string;
      topicId?: string;
      text?: string;
      imageFileId?: string;
    },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const teamId =
      typeof payload?.teamId === 'string' ? payload.teamId.trim() : '';
    const topicId =
      typeof payload?.topicId === 'string' ? payload.topicId.trim() : '';
    const text = typeof payload?.text === 'string' ? payload.text : '';
    const imageFileId =
      typeof payload?.imageFileId === 'string'
        ? payload.imageFileId.trim()
        : undefined;
    if (!teamId || !topicId) {
      throw new WsException('teamId and topicId are required');
    }
    this.assertWsMessageSendRate(client.data.user.userId);

    const message = await this.forumService.postMessage(
      teamId,
      topicId,
      client.data.user.userId,
      text,
      imageFileId || null,
    );

    this.broadcastNewMessage(teamId, topicId, message);
    return { event: 'message:sent', data: message };
  }

  @SubscribeMessage('typing')
  async notifyTyping(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() body: { teamId?: string; topicId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized websocket connection');
    }
    const teamId =
      typeof body?.teamId === 'string' ? body.teamId.trim() : '';
    const topicId =
      typeof body?.topicId === 'string' ? body.topicId.trim() : '';
    if (!teamId || !topicId) {
      throw new WsException('teamId and topicId are required');
    }
    this.server?.to(this.roomKey(teamId, topicId)).except(client.id).emit(
      'user:typing',
      {
        teamId,
        topicId,
        userId: client.data.user.userId,
        username: client.data.user.username,
      },
    );
  }

  broadcastNewMessage(teamId: string, topicId: string, message: TeamForumMessageRow) {
    this.server
      ?.to(this.roomKey(teamId, topicId))
      .emit('message:new', { ...message, teamId, topicId });
  }

  broadcastMessageDeleted(
    teamId: string,
    topicId: string,
    messageId: string,
    deletedAt: string,
    deletedByUserId: string,
  ) {
    this.server?.to(this.roomKey(teamId, topicId)).emit('message:deleted', {
      teamId,
      topicId,
      messageId,
      deletedAt,
      deletedByUserId,
    });
  }

  broadcastMessageEdited(teamId: string, topicId: string, message: TeamForumMessageRow) {
    this.server
      ?.to(this.roomKey(teamId, topicId))
      .emit('message:edited', { ...message, teamId, topicId });
  }

  private assertWsMessageSendRate(userId: string): void {
    const now = Date.now();
    const prev = this.wsMessageSendTimestamps.get(userId) ?? [];
    const recent = prev.filter((t) => now - t < WS_MESSAGE_WINDOW_MS);
    if (recent.length >= WS_MESSAGE_MAX) {
      throw new WsException('Too many messages, try again shortly');
    }
    recent.push(now);
    this.wsMessageSendTimestamps.set(userId, recent);
  }
}
