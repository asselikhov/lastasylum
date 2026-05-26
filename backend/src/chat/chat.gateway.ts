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
import { Namespace, Socket } from 'socket.io';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { authenticateSocketConnection } from '../common/socket-auth.util';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { userMayAccessChatRoom } from './chat-room-access';
import { UsersService } from '../users/users.service';
import { ChatRoomsService } from './chat-rooms.service';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { CreateMessageDto } from './dto/create-message.dto';
import { ChatService } from './chat.service';
import { ALLIANCE_RAID_ROOM_TITLE } from '../common/constants/chat-room-constants';
import { Types } from 'mongoose';

/** Must match overlay reaction ids in Android OverlayQuickReactions.kt */
const ALLOWED_OVERLAY_ANIMATION_REACTIONS = [
  'heart',
  'doggie',
  'wumpus_angry',
  'crying_smoothymon',
  'plane_heart',
  'cat_love',
  'cat_playing',
] as const;

/** GIF tiles in Android overlay Animations tab (overlay_reaction_gif_01..23). */
const ALLOWED_OVERLAY_GIF_REACTIONS = Array.from(
  { length: 37 },
  (_, i) => `gif_${String(i + 1).padStart(2, '0')}`,
);

/** Must match overlay_meme_01..25 drawables in OverlayQuickReactions.kt */
const ALLOWED_OVERLAY_MEME_REACTIONS = Array.from(
  { length: 25 },
  (_, i) => `meme_${String(i + 1).padStart(2, '0')}`,
);

const ALLOWED_OVERLAY_STICKER_REACTIONS = Array.from(
  { length: 3 },
  (_, i) => `sticker_${String(i + 1).padStart(2, '0')}`,
);

const ALLOWED_OVERLAY_REACTIONS = new Set<string>([
  ...ALLOWED_OVERLAY_ANIMATION_REACTIONS,
  ...ALLOWED_OVERLAY_GIF_REACTIONS,
  ...ALLOWED_OVERLAY_MEME_REACTIONS,
  ...ALLOWED_OVERLAY_STICKER_REACTIONS,
]);

/** Android OverlayTextReaction.kt — prefix + Base64 URL-safe UTF-8 payload. */
const OVERLAY_TEXT_REACTION_PREFIX = 'text:';
const OVERLAY_TEXT_REACTION_MAX_CHARS = 200;
const OVERLAY_TEXT_REACTION_MAX_ENCODED_LEN = 512;

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
  server: Namespace;

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
    const user = authenticateSocketConnection(
      client,
      this.jwtService,
      this.configService,
    );
    /** Personal room for push-style overlay signals (reactions, etc.). */
    void client.join(`user:${user.userId}`);
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
    const message = await this.chatService.createMessage({
      roomId: payload.roomId.trim(),
      text: payload.text,
      replyToMessageId: payload.replyToMessageId,
      author: client.data.user,
    });

    const roomId = payload.roomId.trim();
    this.broadcastNewMessageWithOverlayFanout(
      roomId,
      message,
      client.data.user.userId,
    );
    void this.emitUnreadSnapshotsForRoom(roomId, client.data.user.userId);
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
    if (r.startsWith(OVERLAY_TEXT_REACTION_PREFIX)) {
      return this.normalizeTextOverlayReaction(r) ?? 'heart';
    }
    return ALLOWED_OVERLAY_REACTIONS.has(r) ? r : 'heart';
  }

  private normalizeTextOverlayReaction(raw: string): string | null {
    if (raw.length > OVERLAY_TEXT_REACTION_MAX_ENCODED_LEN) return null;
    const payload = raw.slice(OVERLAY_TEXT_REACTION_PREFIX.length);
    if (!payload) return null;
    try {
      const decoded = Buffer.from(payload, 'base64url').toString('utf8');
      const cleaned = decoded
        .replace(/[\u0000-\u0008\u000e-\u001f]/g, '')
        .replace(/\s+/g, ' ')
        .trim();
      if (!cleaned || cleaned.length > OVERLAY_TEXT_REACTION_MAX_CHARS) {
        return null;
      }
      const reencoded = Buffer.from(cleaned, 'utf8').toString('base64url');
      return `${OVERLAY_TEXT_REACTION_PREFIX}${reencoded}`;
    } catch {
      return null;
    }
  }

  private overlayReactionPayload(
    sender: GatewayUser,
    targetUserId: string,
    reaction: string,
    broadcast: boolean,
  ) {
    return {
      fromUserId: sender.userId,
      fromUsername: sender.username,
      reaction,
      targetUserId,
      broadcast,
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
        this.overlayReactionPayload(
          client.data.user,
          targetUserId,
          reaction,
          false,
        ),
      );

    return { event: 'overlay:reaction:sent', data: { targetUserId, reaction } };
  }

  /**
   * Broadcast overlay reaction to all teammates in game with a fresh overlay ping.
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
            true,
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

  /**
   * Raid strip: teammates in-game with a fresh overlay heartbeat may not have joined
   * `chat:roomId` yet — also push `message:new` on their personal `user:` socket room.
   */
  broadcastNewMessageWithOverlayFanout(
    roomId: string,
    message: unknown,
    senderUserId: string,
  ): void {
    this.broadcastNewMessage(roomId, message);
    void this.fanOutRaidMessageToIngameOverlayTeammates(
      roomId,
      message,
      senderUserId,
    );
  }

  private async fanOutRaidMessageToIngameOverlayTeammates(
    roomId: string,
    message: unknown,
    senderUserId: string,
  ): Promise<void> {
    const rid = roomId?.trim();
    const uid = senderUserId?.trim();
    if (!rid || !uid) return;
    const room = await this.chatRoomsService.findById(rid);
    if (!room || room.title !== ALLIANCE_RAID_ROOM_TITLE) return;
    const teammateIds =
      await this.usersService.listOverlayIngameTeammateIds(uid);
    for (const teammateId of teammateIds) {
      this.server?.to(`user:${teammateId}`).emit('message:new', message);
    }
  }

  /** Per-user unread snapshot (personal `user:` socket room). */
  async emitUnreadToUser(userId: string, roomId: string): Promise<void> {
    const uid = userId?.trim();
    const rid = roomId?.trim();
    if (!uid || !rid) return;
    const unreadMap = await this.chatService.countUnreadByRoomIds(uid, [rid]);
    const lastReadMap =
      await this.chatService.getLastReadMessageIdsByRoomIds(uid, [rid]);
    this.server?.to(`user:${uid}`).emit('rooms:unread', {
      roomId: rid,
      unreadCount: unreadMap.get(rid) ?? 0,
      lastReadMessageId: lastReadMap.get(rid) ?? null,
    });
  }

  /** Push per-user unread after a new message (HTTP or WS), including overlay FGS without room:join. */
  async notifyRoomUnreadAfterNewMessage(
    roomId: string,
    excludeUserId: string,
  ): Promise<void> {
    await this.emitUnreadSnapshotsForRoom(roomId, excludeUserId);
  }

  private async emitUnreadSnapshotsForRoom(
    roomId: string,
    excludeUserId: string,
  ): Promise<void> {
    const notified = new Set<string>();
    const adapterRoom = this.server?.adapter.rooms.get(`chat:${roomId}`);
    if (adapterRoom) {
      for (const socketId of adapterRoom) {
        const client = this.server.sockets.get(socketId) as AuthSocket | undefined;
        const userId = client?.data?.user?.userId;
        if (!userId || userId === excludeUserId || notified.has(userId)) continue;
        notified.add(userId);
        await this.emitUnreadToUser(userId, roomId);
      }
    }
    const overlayTeammates =
      await this.usersService.listOverlayIngameTeammateIds(excludeUserId);
    for (const userId of overlayTeammates) {
      if (notified.has(userId)) continue;
      notified.add(userId);
      await this.emitUnreadToUser(userId, roomId);
    }
  }

  broadcastMessageDeleted(roomId: string, payload: unknown) {
    this.server?.to(`chat:${roomId}`).emit('message:deleted', payload);
  }
}
