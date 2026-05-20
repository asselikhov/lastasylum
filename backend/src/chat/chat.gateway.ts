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
import { userMayAccessChatRoom } from './chat-room-access';
import { UsersService } from '../users/users.service';
import { ChatRoomsService } from './chat-rooms.service';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { CreateMessageDto } from './dto/create-message.dto';
import { ChatService } from './chat.service';
import { Types } from 'mongoose';

/** Align with HTTP POST /chat/messages throttling (8 sends per 10s window). */
const WS_MESSAGE_WINDOW_MS = 10_000;
const WS_MESSAGE_MAX = 8;

/** Quick overlay reaction burst (e.g. heart) — slightly looser cap than chat messages. */
const WS_OVERLAY_REACTION_WINDOW_MS = 10_000;
const WS_OVERLAY_REACTION_MAX = 15;

const ALLOWED_OVERLAY_REACTIONS = new Set(['heart', 'thumbs', 'fire', 'star']);

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type SocketData = {
  user?: GatewayUser;
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

  /** userId -> timestamps of overlay:reaction (sliding window). */
  private readonly wsOverlayReactionTimestamps = new Map<string, number[]>();

  /** socketId:roomId -> last typing broadcast (ms). */
  private readonly wsTypingLastEmit = new Map<string, number>();

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

    /** Personal room for push-style overlay signals (reactions, etc.). */
    void client.join(`user:${payload.sub}`);
  }

  @SubscribeMessage('room:join')
  async joinRoom(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: { roomId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const roomId =
      typeof payload?.roomId === 'string' ? payload.roomId.trim() : '';
    if (!roomId) {
      throw new WsException('roomId is required');
    }

    await this.chatService.assertUserMayUseChat(client.data.user.userId);

    const user = await this.usersService.findById(client.data.user.userId);
    const room = await this.chatRoomsService.findById(roomId);
    if (!user || !room || room.archivedAt) {
      throw new WsException('Room not found');
    }
    if (!userMayAccessChatRoom(user, room)) {
      throw new WsException('Room is not available for your alliance');
    }
    if (
      this.usersService.effectiveMembership(user) !==
      TeamMembershipStatus.ACTIVE
    ) {
      throw new WsException('Chat is not available for this account');
    }

    const key = `chat:${roomId}`;
    /** Allow multiple chat rooms per socket (e.g. selected room + «Рейд» for overlay). */
    void client.join(key);

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

  @SubscribeMessage('typing')
  async notifyTyping(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() body: { roomId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized websocket connection');
    }
    const roomId = typeof body?.roomId === 'string' ? body.roomId.trim() : '';
    if (!roomId) {
      throw new WsException('roomId is required');
    }
    await this.chatService.assertUserMayUseChat(client.data.user.userId);
    const user = await this.usersService.findById(client.data.user.userId);
    const room = await this.chatRoomsService.findById(roomId);
    if (!user || !room || room.archivedAt) {
      throw new WsException('Room not found');
    }
    if (!userMayAccessChatRoom(user, room)) {
      throw new WsException('Room is not available for your alliance');
    }
    if (
      this.usersService.effectiveMembership(user) !==
      TeamMembershipStatus.ACTIVE
    ) {
      throw new WsException('Chat is not available for this account');
    }
    const throttleKey = `${client.id}:${roomId}`;
    const now = Date.now();
    const last = this.wsTypingLastEmit.get(throttleKey) ?? 0;
    if (now - last < 1_500) {
      return { event: 'typing:throttled' };
    }
    this.wsTypingLastEmit.set(throttleKey, now);
    if (this.wsTypingLastEmit.size > 500) {
      for (const [key, ts] of this.wsTypingLastEmit) {
        if (now - ts > 60_000) this.wsTypingLastEmit.delete(key);
      }
    }
    this.server?.to(`chat:${roomId}`).except(client.id).emit('user:typing', {
      roomId,
      userId: client.data.user.userId,
      username: client.data.user.username,
    });
  }

  private normalizeOverlayReaction(raw: unknown): string {
    const r = typeof raw === 'string' ? raw.trim() : '';
    return ALLOWED_OVERLAY_REACTIONS.has(r) ? r : 'heart';
  }

  private overlayReactionPayload(
    sender: GatewayUser,
    targetUserId: string,
    reaction: string,
  ) {
    return {
      fromUserId: sender.userId,
      fromUsername: sender.username,
      reaction,
      targetUserId,
    };
  }

  /**
   * Send a lightweight fullscreen overlay reaction to a teammate (same player team only).
   * Delivered to the recipient's sockets via room `user:<targetUserId>`.
   */
  @SubscribeMessage('overlay:reaction')
  async sendOverlayReaction(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    body: { targetUserId?: string; reaction?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const targetUserId =
      typeof body?.targetUserId === 'string' ? body.targetUserId.trim() : '';
    if (!targetUserId || !Types.ObjectId.isValid(targetUserId)) {
      throw new WsException('targetUserId is required');
    }
    const reaction = this.normalizeOverlayReaction(body?.reaction);

    if (targetUserId === client.data.user.userId) {
      throw new WsException('Invalid recipient');
    }

    this.assertWsOverlayReactionRate(client.data.user.userId);

    await this.chatService.assertUserMayUseChat(client.data.user.userId);

    const sender = await this.usersService.findById(client.data.user.userId);
    const target = await this.usersService.findById(targetUserId);
    if (!sender || !target) {
      throw new WsException('User not found');
    }
    if (
      this.usersService.effectiveMembership(sender) !==
        TeamMembershipStatus.ACTIVE ||
      this.usersService.effectiveMembership(target) !==
        TeamMembershipStatus.ACTIVE
    ) {
      throw new WsException('Reaction is not available for this account');
    }

    const senderTeamId = sender.playerTeamId?.toString() ?? null;
    const targetTeamId = target.playerTeamId?.toString() ?? null;
    if (
      !senderTeamId ||
      !targetTeamId ||
      senderTeamId !== targetTeamId
    ) {
      throw new WsException('Recipient is not in your team');
    }

    this.server
      ?.to(`user:${targetUserId}`)
      .emit(
        'overlay:reaction',
        this.overlayReactionPayload(client.data.user, targetUserId, reaction),
      );

    return { event: 'overlay:reaction:sent', data: { targetUserId, reaction } };
  }

  /**
   * Broadcast overlay reaction to all teammates in game with a fresh overlay ping.
   * Counts as a single send for rate limiting (not per recipient).
   */
  @SubscribeMessage('overlay:reaction:broadcast')
  async broadcastOverlayReaction(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() body: { reaction?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const reaction = this.normalizeOverlayReaction(body?.reaction);

    this.assertWsOverlayReactionRate(client.data.user.userId);

    await this.chatService.assertUserMayUseChat(client.data.user.userId);

    const sender = await this.usersService.findById(client.data.user.userId);
    if (!sender) {
      throw new WsException('User not found');
    }
    if (
      this.usersService.effectiveMembership(sender) !==
      TeamMembershipStatus.ACTIVE
    ) {
      throw new WsException('Reaction is not available for this account');
    }
    if (!sender.playerTeamId) {
      throw new WsException('You are not in a team');
    }

    const targetUserIds =
      await this.usersService.listOverlayIngameTeammateIds(
        client.data.user.userId,
      );

    for (const targetUserId of targetUserIds) {
      this.server
        ?.to(`user:${targetUserId}`)
        .emit(
          'overlay:reaction',
          this.overlayReactionPayload(
            client.data.user,
            targetUserId,
            reaction,
          ),
        );
    }

    return {
      event: 'overlay:reaction:broadcast:sent',
      data: { reaction, recipientCount: targetUserIds.length },
    };
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

  private assertWsOverlayReactionRate(userId: string): void {
    const now = Date.now();
    const prev = this.wsOverlayReactionTimestamps.get(userId) ?? [];
    const recent = prev.filter((t) => now - t < WS_OVERLAY_REACTION_WINDOW_MS);
    if (recent.length >= WS_OVERLAY_REACTION_MAX) {
      throw new WsException('Too many reactions, try again shortly');
    }
    recent.push(now);
    this.wsOverlayReactionTimestamps.set(userId, recent);
  }
}
