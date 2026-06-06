import { Logger } from '@nestjs/common';
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
import {
  GLOBAL_CHAT_ALLIANCE_ID,
  ALLIANCE_RAID_ROOM_TITLE,
} from '../common/constants/chat-room-constants';
import { Types } from 'mongoose';
import { PushNotificationsService } from '../push/push-notifications.service';
import { ChatAttachmentsService } from './chat-attachments.service';
import { OverlayReactionLogService } from './overlay-reaction-log.service';
import { normalizeOverlayChatStickerReaction } from './overlay-sticker-reaction.util';
import { filterPersonalChatFanoutUserIds } from './chat-realtime-broadcast.util';
import { resolveGameEventId } from '../game-events/game-event-catalog';
import { formatGameEventPushSenderLine } from '../game-events/game-event-push.util';

function gameEventPushSenderFromMessage(message: unknown): {
  senderName: string;
  senderTelegramUsername: string;
  senderSquadRole: string;
  senderTeamTag: string;
  senderServerNumber: number | null;
} {
  const m = message as {
    senderUsername?: string;
    senderTelegramUsername?: string | null;
    senderRole?: string;
    senderTeamTag?: string | null;
    senderServerNumber?: number | null;
  };
  const serverRaw = m.senderServerNumber;
  const server =
    typeof serverRaw === 'number' && serverRaw >= 1 ? serverRaw : null;
  return {
    senderName: (m.senderUsername ?? '').trim(),
    senderTelegramUsername: (m.senderTelegramUsername ?? '').trim(),
    senderSquadRole: (m.senderRole ?? '').trim().toUpperCase(),
    senderTeamTag: (m.senderTeamTag ?? '').trim(),
    senderServerNumber: server,
  };
}

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

/** GIF tiles in Android overlay Animations tab (overlay_reaction_gif_01..45). */
const ALLOWED_OVERLAY_GIF_REACTIONS = Array.from(
  { length: 45 },
  (_, i) => `gif_${String(i + 1).padStart(2, '0')}`,
);

/** Must match overlay_meme_01..39 drawables in OverlayQuickReactions.kt */
const ALLOWED_OVERLAY_MEME_REACTIONS = Array.from(
  { length: 39 },
  (_, i) => `meme_${String(i + 1).padStart(2, '0')}`,
);

const ALLOWED_OVERLAY_STICKER_REACTIONS = Array.from(
  { length: 20 },
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
  private readonly logger = new Logger(ChatGateway.name);

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
    private readonly pushNotifications: PushNotificationsService,
    private readonly attachmentsService: ChatAttachmentsService,
    private readonly overlayReactionLogService: OverlayReactionLogService,
  ) {}

  handleConnection(client: AuthSocket) {
    const user = authenticateSocketConnection(
      client,
      this.jwtService,
      this.configService,
    );
    /** Personal room for push-style overlay signals (reactions, etc.). */
    void client.join(`user:${user.userId}`);
    void this.emitAllUnreadSnapshotsOnConnect(user.userId);
  }

  /** Full unread snapshot after reconnect/reinstall so badges match server read state. */
  private async emitAllUnreadSnapshotsOnConnect(userId: string): Promise<void> {
    const uid = userId?.trim();
    if (!uid) return;
    try {
      const user = await this.usersService.findById(uid);
      if (!user) return;
      const rooms = await this.chatRoomsService.listRoomsVisibleToUser(user);
      for (const room of rooms) {
        await this.emitUnreadToUser(uid, room.id);
      }
    } catch (err) {
      this.logger.warn(
        `emitAllUnreadSnapshotsOnConnect failed user=${uid}: ${err instanceof Error ? err.message : err}`,
      );
    }
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
    await client.join(key);

    return { event: 'room:joined', data: { roomId } };
  }

  @SubscribeMessage('room:leave')
  async leaveRoom(
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
    await client.leave(`chat:${roomId}`);
    return { event: 'room:left', data: { roomId } };
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
    const roomId = payload.roomId.trim();
    const attachmentIds = Array.isArray(payload.attachments)
      ? payload.attachments.filter((id) => typeof id === 'string' && id.trim())
      : [];
    const anyChat = this.chatService as unknown as {
      assertRoomForUser: (
        userId: string,
        roomId: string,
      ) => Promise<{ allianceId: string; roomObjectId: Types.ObjectId }>;
    };
    const { allianceId, roomObjectId } = await anyChat.assertRoomForUser(
      client.data.user.userId,
      roomId,
    );
    const resolvedAttachments = await this.attachmentsService.resolveForRoom({
      allianceId,
      roomObjectId,
      attachmentIds,
    });
    const message = await this.chatService.createMessage({
      roomId,
      text: payload.text ?? '',
      replyToMessageId: payload.replyToMessageId,
      author: client.data.user,
      attachments: resolvedAttachments,
      clientMessageId: payload.clientMessageId,
    });
    const messageId =
      typeof (message as { _id?: unknown })._id === 'string'
        ? (message as { _id: string })._id
        : typeof (message as { _id?: unknown })._id === 'object' &&
            (message as { _id?: { toString?: () => string } })._id != null
          ? (message as { _id: { toString: () => string } })._id.toString()
          : '';
    const gameEventId = resolveGameEventId(
      payload.gameEventAlert,
      payload.excavationAlert,
    );
    await this.afterMessageCreated({
      roomId,
      message,
      senderUserId: client.data.user.userId,
      gameEventId,
      gameEventText: payload.text?.trim() ?? '',
      messageAllianceId:
        typeof (message as { allianceId?: string }).allianceId === 'string'
          ? (message as { allianceId: string }).allianceId
          : allianceId,
      messageId,
      senderName: client.data.user.username,
    });
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
    const chatSticker = normalizeOverlayChatStickerReaction(r);
    if (chatSticker) return chatSticker;
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
    logEntry?: {
      _id: string;
      replyToLogId?: string | null;
      replyToLog?: {
        _id: string;
        reaction: string;
        visibility: 'personal' | 'broadcast';
        senderUserId: string;
        senderUsername: string;
        targetUserId: string | null;
        targetUsername: string | null;
      } | null;
    },
  ) {
    const payload: Record<string, unknown> = {
      fromUserId: sender.userId,
      fromUsername: sender.username,
      reaction,
      targetUserId,
      broadcast,
    };
    if (logEntry?._id) {
      payload.logEntryId = logEntry._id;
    }
    if (logEntry?.replyToLogId) {
      payload.replyToLogId = String(logEntry.replyToLogId);
    }
    if (logEntry?.replyToLog) {
      const snap = logEntry.replyToLog;
      payload.replyToLog = {
        _id: String(snap._id),
        reaction: snap.reaction,
        visibility: snap.visibility,
        senderUserId: snap.senderUserId,
        senderUsername: snap.senderUsername,
        targetUserId: snap.targetUserId,
        targetUsername: snap.targetUsername,
      };
    }
    return payload;
  }

  /**
   * Send a lightweight fullscreen overlay reaction to a teammate (same player team only).
   * Delivered to the recipient's sockets via room `user:<targetUserId>`.
   */
  @SubscribeMessage('overlay:reaction')
  async sendOverlayReaction(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    body: { targetUserId?: string; reaction?: string; replyToLogId?: string },
  ) {
    const strayReplyToLogId =
      typeof body?.replyToLogId === 'string' ? body.replyToLogId.trim() : '';
    if (strayReplyToLogId) {
      throw new WsException(
        'replyToLogId is not allowed on overlay:reaction; use overlay:reaction:reply',
      );
    }
    return this.sendPersonalOverlayReaction(client, body, {
      deliveryEvent: 'overlay:reaction',
      ackEvent: 'overlay:reaction:sent',
      replyToLogId: null,
    });
  }

  /** Reply to an existing overlay reaction log entry (notifications «Ответить реакцией»). */
  @SubscribeMessage('overlay:reaction:reply')
  async sendOverlayReactionReply(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    body: { targetUserId?: string; reaction?: string; replyToLogId?: string },
  ) {
    const replyToLogId =
      typeof body?.replyToLogId === 'string' ? body.replyToLogId.trim() : '';
    if (!replyToLogId) {
      throw new WsException('replyToLogId is required');
    }
    return this.sendPersonalOverlayReaction(client, body, {
      deliveryEvent: 'overlay:reaction:reply',
      ackEvent: 'overlay:reaction:reply:sent',
      replyToLogId,
    });
  }

  private async sendPersonalOverlayReaction(
    client: AuthSocket,
    body: { targetUserId?: string; reaction?: string },
    options: {
      deliveryEvent: 'overlay:reaction' | 'overlay:reaction:reply';
      ackEvent: string;
      replyToLogId: string | null;
    },
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
    if (!senderTeamId || !targetTeamId || senderTeamId !== targetTeamId) {
      throw new WsException('Recipient is not in your team');
    }

    const { entry: logEntry, recipientUserIds } =
      await this.overlayReactionLogService.createPersonal({
        sender,
        target,
        reaction,
        replyToLogId: options.replyToLogId,
      });

    this.server
      ?.to(`user:${targetUserId}`)
      .emit(
        options.deliveryEvent,
        this.overlayReactionPayload(
          client.data.user,
          targetUserId,
          reaction,
          false,
          logEntry,
        ),
      );

    this.emitOverlayReactionLog(logEntry, recipientUserIds);

    return {
      event: options.ackEvent,
      data: {
        targetUserId,
        reaction,
        replyToLogId: options.replyToLogId,
      },
    };
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

    const targetUserIds = await this.usersService.listOverlayIngameTeammateIds(
      client.data.user.userId,
    );

    const logEntry = await this.overlayReactionLogService.createBroadcast({
      sender,
      reaction,
    });

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
            logEntry,
          ),
        );
    }

    const logRecipients = [
      client.data.user.userId,
      ...targetUserIds,
    ];
    this.emitOverlayReactionLog(logEntry, logRecipients);

    return {
      event: 'overlay:reaction:broadcast:sent',
      data: { reaction, recipientCount: targetUserIds.length },
    };
  }

  private emitOverlayReactionLog(
    logEntry: {
      _id: string;
      senderUserId: string;
      senderUsername: string;
      targetUserId: string | null;
      targetUsername: string | null;
      reaction: string;
      visibility: 'personal' | 'broadcast';
      createdAt: string;
      reactions?: {
        emoji: string;
        count: number;
        reactedByMe: boolean;
      }[];
      replyToLogId?: string | null;
      replyToLog?: {
        _id: string;
        reaction: string;
        visibility: 'personal' | 'broadcast';
        senderUserId: string;
        senderUsername: string;
        targetUserId: string | null;
        targetUsername: string | null;
      } | null;
    },
    userIds: string[],
  ) {
    const unique = [...new Set(userIds.filter((id) => id.trim()))];
    for (const userId of unique) {
      this.server?.to(`user:${userId}`).emit('overlay:reaction:log', {
        logEntry,
      });
    }
  }

  emitOverlayReactionLogReaction(
    logEntry: {
      _id: string;
      senderUserId: string;
      senderUsername: string;
      targetUserId: string | null;
      targetUsername: string | null;
      reaction: string;
      visibility: 'personal' | 'broadcast';
      createdAt: string;
      reactions: {
        emoji: string;
        count: number;
        reactedByMe: boolean;
      }[];
    },
    userIds: string[],
  ) {
    const unique = [...new Set(userIds.filter((id) => id.trim()))];
    for (const userId of unique) {
      this.server?.to(`user:${userId}`).emit('overlay:reaction:log:reaction', {
        logEntry,
      });
    }
  }

  /**
   * HTTP + WS: broadcast message, unread snapshots, optional game-event push.
   */
  async afterMessageCreated(input: {
    roomId: string;
    message: unknown;
    senderUserId: string;
    gameEventId?: string | null;
    gameEventText?: string;
    messageAllianceId?: string;
    messageId?: string;
    senderName?: string;
  }): Promise<void> {
    const roomId = input.roomId.trim();
    const senderUserId = input.senderUserId.trim();
    if (!roomId || !senderUserId) return;
    const eventId = input.gameEventId?.trim();
    this.broadcastNewMessageWithOverlayFanout(
      roomId,
      input.message,
      senderUserId,
    );
    await this.notifyRoomUnreadAfterNewMessage(roomId, senderUserId);
    if (
      eventId &&
      input.messageAllianceId &&
      input.messageAllianceId !== GLOBAL_CHAT_ALLIANCE_ID
    ) {
      const pushSender = gameEventPushSenderFromMessage(input.message);
      const senderName =
        pushSender.senderName || (input.senderName ?? '').trim();
      const senderLine = formatGameEventPushSenderLine({
        username: senderName,
        teamTag: pushSender.senderTeamTag || null,
        serverNumber: pushSender.senderServerNumber,
      });
      const senderTeamDisplayName =
        await this.usersService.resolveTeamDisplayNameForGameEventPush(
          senderUserId,
          input.messageAllianceId,
        );
      let senderTelegramUsername = pushSender.senderTelegramUsername;
      if (!senderTelegramUsername) {
        const telegramMap =
          await this.usersService.findTelegramUsernamesByIds([senderUserId]);
        senderTelegramUsername = (telegramMap.get(senderUserId) ?? '').trim();
      }
      void this.pushNotifications
        .notifyGameEventAlert({
          allianceId: input.messageAllianceId,
          excludeUserId: senderUserId,
          eventId,
          senderName,
          senderLine,
          senderTelegramUsername,
          senderSquadRole: pushSender.senderSquadRole,
          senderTeamTag: pushSender.senderTeamTag,
          senderServerNumber: pushSender.senderServerNumber,
          senderTeamDisplayName,
          body: input.gameEventText ?? '',
          data: {
            roomId,
            messageId: input.messageId ?? '',
          },
        })
        .catch(() => undefined);
    }
  }

  /**
   * Emit to joined `chat:{roomId}` sockets and mirror on `user:{id}` for eligible users not in the room.
   */
  broadcastRoomPinChanged(payload: {
    roomId: string;
    pinnedMessageId: string | null;
    pinnedAt: string | null;
    pinnedByUserId: string | null;
    pinnedMessage: unknown;
  }): void {
    this.broadcastChatRoomEvent(payload.roomId, 'room:pin-changed', payload);
    void this.maybeNotifyRaidPinPush(payload);
  }

  private async maybeNotifyRaidPinPush(payload: {
    roomId: string;
    pinnedMessageId: string | null;
    pinnedByUserId: string | null;
    pinnedMessage: unknown;
  }): Promise<void> {
    const pinId = payload.pinnedMessageId?.trim() ?? '';
    if (!pinId) return;
    const room = await this.chatRoomsService.findById(payload.roomId);
    if (!room || room.title !== ALLIANCE_RAID_ROOM_TITLE) return;
    const allianceId = room.allianceId?.trim() ?? '';
    if (!allianceId.startsWith('pt:')) return;
    const preview = payload.pinnedMessage as { text?: string } | null;
    const body = preview?.text?.trim() || 'Закреплено сообщение';
    await this.pushNotifications.notifyRaidPinAlert({
      allianceId,
      excludeUserId: payload.pinnedByUserId?.trim() ?? '',
      roomId: payload.roomId,
      messageId: pinId,
      body,
    });
  }

  broadcastChatRoomEvent(
    roomId: string,
    event: string,
    payload: unknown,
    excludeUserId?: string,
  ): void {
    const rid = roomId.trim();
    if (!rid) return;
    this.server?.to(`chat:${rid}`).emit(event, payload);
    void this.fanOutChatEventToEligibleUsersNotInRoom(
      rid,
      event,
      payload,
      excludeUserId,
    );
  }

  broadcastNewMessageWithOverlayFanout(
    roomId: string,
    message: unknown,
    senderUserId: string,
  ): void {
    this.broadcastNewMessage(roomId, message);
    void this.fanOutNewMessageToEligibleOverlayClients(
      roomId,
      message,
      senderUserId,
    );
  }

  /** Paths B (eligible personal) + C (raid ingame teammates) without duplicate delivery. */
  private async fanOutNewMessageToEligibleOverlayClients(
    roomId: string,
    message: unknown,
    senderUserId: string,
  ): Promise<void> {
    const rid = roomId.trim();
    const uid = senderUserId.trim();
    if (!rid || !uid) return;
    const inRoom = this.userIdsInChatRoom(rid);
    const personalTargets = await this.fanOutChatEventToEligibleUsersNotInRoom(
      rid,
      'message:new',
      message,
      uid,
      inRoom,
    );
    await this.fanOutRaidMessageToIngameOverlayTeammates(
      rid,
      message,
      uid,
      inRoom,
      personalTargets,
    );
  }

  private async fanOutChatEventToEligibleUsersNotInRoom(
    roomId: string,
    event: string,
    payload: unknown,
    excludeUserId?: string,
    inRoomOverride?: Set<string>,
  ): Promise<Set<string>> {
    const eligible = await this.listEligibleUserIdsForRoom(roomId);
    const inRoom = inRoomOverride ?? this.userIdsInChatRoom(roomId);
    const targets = filterPersonalChatFanoutUserIds(
      eligible,
      inRoom,
      excludeUserId,
    );
    for (const userId of targets) {
      // Personal socket for overlay/FGS clients that missed `chat:room` join.
      this.server?.to(`user:${userId}`).emit(event, payload);
    }
    return new Set(targets);
  }

  private userIdsInChatRoom(roomId: string): Set<string> {
    const out = new Set<string>();
    const adapterRoom = this.server?.adapter.rooms.get(`chat:${roomId}`);
    if (!adapterRoom) return out;
    for (const socketId of adapterRoom) {
      const client = this.server.sockets.get(socketId) as
        | AuthSocket
        | undefined;
      const userId = client?.data?.user?.userId?.trim();
      if (userId) out.add(userId);
    }
    return out;
  }

  private async listEligibleUserIdsForRoom(roomId: string): Promise<string[]> {
    const room = await this.chatRoomsService.findById(roomId);
    if (!room) return [];
    return this.usersService.listActiveUserIdsForChatRoomAccess(room);
  }

  broadcastNewMessage(roomId: string, message: unknown) {
    this.server?.to(`chat:${roomId}`).emit('message:new', message);
  }

  private async fanOutRaidMessageToIngameOverlayTeammates(
    roomId: string,
    message: unknown,
    senderUserId: string,
    inRoom: Set<string>,
    personalTargets: Set<string>,
  ): Promise<void> {
    const rid = roomId?.trim();
    const uid = senderUserId?.trim();
    if (!rid || !uid) return;
    const room = await this.chatRoomsService.findById(rid);
    if (!room || room.title !== ALLIANCE_RAID_ROOM_TITLE) return;
    const teammateIds =
      await this.usersService.listSquadTeammateUserIdsForRaidFanout(uid);
    let fanoutCount = 0;
    let skippedInRoom = 0;
    let skippedPersonal = 0;
    for (const teammateId of teammateIds) {
      if (teammateId === uid) continue;
      if (inRoom.has(teammateId)) {
        skippedInRoom++;
        continue;
      }
      if (personalTargets.has(teammateId)) {
        skippedPersonal++;
        continue;
      }
      this.server?.to(`user:${teammateId}`).emit('message:new', message);
      fanoutCount++;
    }
    this.logger.debug(
      `raid overlay fanout room=${rid} teammates=${teammateIds.length} ` +
        `emitted=${fanoutCount} skippedInRoom=${skippedInRoom} skippedPersonal=${skippedPersonal}`,
    );
  }

  /** Per-user unread snapshot (personal `user:` socket room). */
  async emitUnreadToUser(userId: string, roomId: string): Promise<void> {
    const uid = userId?.trim();
    const rid = roomId?.trim();
    if (!uid || !rid) return;
    const unreadMap = await this.chatService.countUnreadByRoomIds(uid, [rid]);
    const lastReadMap = await this.chatService.getLastReadMessageIdsByRoomIds(
      uid,
      [rid],
    );
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
    const exclude = excludeUserId.trim();
    const eligible = await this.listEligibleUserIdsForRoom(roomId);
    for (const userId of eligible) {
      if (userId === exclude) continue;
      await this.emitUnreadToUser(userId, roomId);
    }
  }

  broadcastMessageDeleted(roomId: string, payload: unknown) {
    this.broadcastChatRoomEvent(roomId, 'message:deleted', payload);
  }

  /** Notify connected clients that server-side chat history was wiped. */
  broadcastChatHistoryCleared(): void {
    this.server?.emit('chat:history:cleared', { ok: true });
    void this.broadcastUnreadZeroAfterHistoryClear();
  }

  /** Push unreadCount=0 so clients drop stale badges without waiting for listRooms. */
  private async broadcastUnreadZeroAfterHistoryClear(): Promise<void> {
    const roomIds = await this.chatRoomsService.listActiveRoomIds();
    for (const roomId of roomIds) {
      await this.emitUnreadSnapshotsForRoom(roomId, '');
    }
  }
}
