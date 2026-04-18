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
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UsersService } from '../users/users.service';
import { ChatRoomsService } from './chat-rooms.service';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { CreateMessageDto } from './dto/create-message.dto';
import { ChatService } from './chat.service';

/** Align with HTTP POST /chat/messages throttling (8 sends per 10s window). */
const WS_MESSAGE_WINDOW_MS = 10_000;
const WS_MESSAGE_MAX = 8;

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type SocketData = {
  user?: GatewayUser;
  lastJoinedChatRoom?: string;
};

type AuthSocket = Socket<
  Record<string, never>,
  Record<string, never>,
  Record<string, never>,
  SocketData
>;

@WebSocketGateway({
  namespace: '/chat',
  cors: {
    origin: parseAllowedOriginsFromEnv(process.env.ALLOWED_ORIGINS) ?? true,
  },
})
export class ChatGateway {
  @WebSocketServer()
  server: Server;

  /** userId -> timestamps of message:send (sliding window). */
  private readonly wsMessageSendTimestamps = new Map<string, number[]>();

  constructor(
    private readonly chatService: ChatService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly usersService: UsersService,
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

  @SubscribeMessage('room:join')
  async joinRoom(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: { roomId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const roomId = typeof payload?.roomId === 'string' ? payload.roomId.trim() : '';
    if (!roomId) {
      throw new WsException('roomId is required');
    }

    await this.chatService.assertUserMayUseChat(client.data.user.userId);

    const user = await this.usersService.findById(client.data.user.userId);
    const room = await this.chatRoomsService.findById(roomId);
    if (!user || !room || room.archivedAt) {
      throw new WsException('Room not found');
    }
    if (room.allianceId !== user.allianceName) {
      throw new WsException('Room is not available for your alliance');
    }
    if (this.usersService.effectiveMembership(user) !== TeamMembershipStatus.ACTIVE) {
      throw new WsException('Chat is not available for this account');
    }

    const key = `chat:${roomId}`;
    if (
      client.data.lastJoinedChatRoom &&
      client.data.lastJoinedChatRoom !== key
    ) {
      void client.leave(client.data.lastJoinedChatRoom);
    }
    void client.join(key);
    client.data.lastJoinedChatRoom = key;

    return { event: 'room:joined', data: { roomId } };
  }

  @SubscribeMessage('message:send')
  async handleMessage(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: CreateMessageDto,
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    if (!payload.roomId?.trim()) {
      throw new WsException('roomId is required');
    }
    this.assertWsMessageSendRate(client.data.user.userId);

    const message = await this.chatService.createMessage({
      roomId: payload.roomId.trim(),
      text: payload.text,
      replyToMessageId: payload.replyToMessageId,
      author: client.data.user,
    });

    this.broadcastNewMessage(payload.roomId.trim(), message);
    return { event: 'message:sent', data: message };
  }

  broadcastNewMessage(roomId: string, message: unknown) {
    this.server?.to(`chat:${roomId}`).emit('message:new', message);
  }

  broadcastMessageDeleted(roomId: string, payload: unknown) {
    this.server?.to(`chat:${roomId}`).emit('message:deleted', payload);
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
