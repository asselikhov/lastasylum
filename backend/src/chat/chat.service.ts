import {
  BadRequestException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
  forwardRef,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import {
  GLOBAL_CHAT_ALLIANCE_ID,
  isServerChatScope,
} from '../common/constants/chat-room-constants';
import { parsePlayerTeamIdFromChatScope } from './chat-alliance-scope';
import { userMayAccessChatRoom } from './chat-room-access';
import { isAppAdminRole } from '../common/alliance-role.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import {
  isSquadOfficerRole,
  PlayerTeamMemberRole,
} from '../common/enums/player-team-member-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UserDocument } from '../users/schemas/user.schema';
import { TeamsService } from '../users/teams.service';
import { GameIdentitiesService } from '../users/game-identities.service';
import { UsersService } from '../users/users.service';
import { StickerAccessService } from '../users/sticker-access.service';
import { PinAuditService } from '../users/pin-audit.service';
import { ChatRoomsService } from './chat-rooms.service';
import { buildMessageReactionBroadcastPayload } from './chat-realtime-broadcast.util';
import { Message, MessageAttachment } from './schemas/message.schema';
import { ChatRoomReadState } from './schemas/chat-room-read-state.schema';
import { ChatAttachmentsService } from './chat-attachments.service';
import { assertStickerPayload } from './sticker-payload.util';
import {
  buildPinnedPreviewFromChatMessage,
  buildStubPinnedPreview,
  enrichPinnedPreview,
  PinnedMessagePreview,
} from '../common/pinned-message-preview';
import {
  ensurePinHistoryMigrated,
  normalizePinHistory,
  type PinHistoryEntry,
} from '../common/pin-history.util';
import {
  ChatRoom,
  ChatRoomDocument,
} from './schemas/chat-room.schema';

type MessageAuthor = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type MessageLean = {
  _id: Types.ObjectId | string;
  allianceId: string;
  roomId: Types.ObjectId | string;
  senderId: string;
  senderUsername: string;
  senderRole: PlayerTeamMemberRole;
  senderTeamTag?: string | null;
  senderServerNumber?: number | null;
  text: string;
  editedAt?: Date | null;
  forwardedFrom?: {
    messageId: Types.ObjectId;
    senderId: string;
    senderUsername: string;
    senderRole: PlayerTeamMemberRole;
    senderTeamTag?: string | null;
    senderServerNumber?: number | null;
  } | null;
  reactions?: { emoji: string; userIds: string[] }[];
  attachments?: MessageAttachment[];
  replyToMessageId?: Types.ObjectId | string | null;
  deletedAt?: Date | null;
  deletedByUserId?: string | null;
  createdAt?: Date | null;
  updatedAt?: Date | null;
};

export type ChatMessageReplyPreview = {
  _id: string;
  senderId: string;
  senderUsername: string;
  senderRole: PlayerTeamMemberRole;
  senderTeamTag: string | null;
  senderServerNumber: number | null;
  text: string;
  createdAt: string | null;
  deletedAt: string | null;
};

export type ChatMessageView = {
  _id: string;
  allianceId: string;
  roomId: string;
  senderId: string;
  senderUsername: string;
  senderRole: PlayerTeamMemberRole;
  /** Three-letter team tag: stored on message or resolved from sender profile. */
  senderTeamTag: string | null;
  senderServerNumber: number | null;
  /** Telegram @handle without @, from sender profile at read time (or at send). */
  senderTelegramUsername: string | null;
  text: string;
  editedAt: string | null;
  forwardedFrom: {
    messageId: string;
    senderId: string;
    senderUsername: string;
    senderRole: PlayerTeamMemberRole;
    senderTeamTag: string | null;
    senderServerNumber: number | null;
  } | null;
  reactions: { emoji: string; count: number; reactedByMe: boolean }[];
  attachments: {
    kind: 'image' | 'file';
    url: string;
    mimeType: string;
    size: number;
    filename: string | null;
  }[];
  createdAt: string | null;
  updatedAt: string | null;
  replyToMessageId: string | null;
  replyTo: ChatMessageReplyPreview | null;
  deletedAt: string | null;
  deletedByUserId: string | null;
};

export type ChatClearHistoryForAdminResult = {
  messagesDeleted: number;
  readStatesDeleted: number;
  attachmentsDeleted: number;
  pinsCleared: number;
};

export type ChatRoomPinChangedPayload = {
  roomId: string;
  pinnedMessageId: string | null;
  pinnedAt: string | null;
  pinnedByUserId: string | null;
  pinnedMessage: PinnedMessagePreview | null;
  pinnedMessages: PinnedMessagePreview[];
};

export type ChatRoomWithPinState = {
  pinnedMessageId: string | null;
  pinnedAt: string | null;
  pinnedByUserId: string | null;
  pinnedMessage: PinnedMessagePreview | null;
  pinnedMessages: PinnedMessagePreview[];
};

@Injectable()
export class ChatService {
  constructor(
    @InjectModel(Message.name) private readonly messageModel: Model<Message>,
    @InjectModel(ChatRoomReadState.name)
    private readonly chatReadStateModel: Model<ChatRoomReadState>,
    private readonly chatAttachments: ChatAttachmentsService,
    private readonly usersService: UsersService,
    private readonly gameIdentities: GameIdentitiesService,
    @Inject(forwardRef(() => TeamsService))
    private readonly teamsService: TeamsService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly stickerAccess: StickerAccessService,
    @Inject(forwardRef(() => PinAuditService))
    private readonly pinAudit: PinAuditService,
  ) {}

  private withSquadDisplayRoles(
    message: MessageLean,
    squadRoleMap: Map<string, PlayerTeamMemberRole>,
  ): MessageLean {
    const senderRole =
      squadRoleMap.get(message.senderId) ?? PlayerTeamMemberRole.R1;
    const forwardedFrom = message.forwardedFrom
      ? {
          ...message.forwardedFrom,
          senderRole:
            squadRoleMap.get(message.forwardedFrom.senderId) ??
            message.forwardedFrom.senderRole,
        }
      : null;
    return { ...message, senderRole, forwardedFrom };
  }

  private async viewMessagesForUser(
    messages: MessageLean[],
    viewerUserId: string,
  ): Promise<ChatMessageView[]> {
    return this.enrichMessages(messages, viewerUserId);
  }

  private async viewMessageForUser(
    message: MessageLean,
    viewerUserId: string,
  ): Promise<ChatMessageView> {
    const [view] = await this.viewMessagesForUser([message], viewerUserId);
    return view;
  }

  async assertUserMayUseChat(userId: string): Promise<void> {
    const u = await this.usersService.findById(userId);
    if (
      !u ||
      this.usersService.effectiveMembership(u) !== TeamMembershipStatus.ACTIVE
    ) {
      throw new ForbiddenException('Chat is not available for this account');
    }
  }

  private hasCompleteTeamBranding(user: UserDocument): boolean {
    const name = user.teamDisplayName?.trim() ?? '';
    const tag = user.teamTag?.trim() ?? '';
    return name.length > 0 && tag.length > 0;
  }

  private assertNotMuted(user: UserDocument): void {
    const now = new Date();
    if (user.mutedUntil && user.mutedUntil > now) {
      throw new ForbiddenException(
        'You are temporarily muted in alliance chat',
      );
    }
  }

  private assertGlobalChatBrandingIfNeeded(
    allianceId: string,
    user: UserDocument,
  ): void {
    if (
      allianceId === GLOBAL_CHAT_ALLIANCE_ID &&
      !this.hasCompleteTeamBranding(user)
    ) {
      throw new ForbiddenException('GLOBAL_CHAT_TEAM_PROFILE_REQUIRED');
    }
  }

  /**
   * Moderate others' messages: app admin (AllianceRole.ADMIN) in «Межсерв» / server rooms;
   * squad R4/R5 in `pt:<teamId>` team chat rooms.
   */
  private async assertMayModerateOthersMessage(
    actor: UserDocument,
    message: MessageLean,
  ): Promise<void> {
    if (message.senderId === actor._id.toString()) {
      return;
    }
    const allianceId = message.allianceId;
    if (
      allianceId === GLOBAL_CHAT_ALLIANCE_ID ||
      isServerChatScope(allianceId)
    ) {
      if (!isAppAdminRole(actor.role)) {
        throw new ForbiddenException(
          'Only administrators can moderate messages in this room',
        );
      }
      return;
    }
    const teamId = parsePlayerTeamIdFromChatScope(allianceId);
    if (teamId) {
      const team = await this.teamsService.getTeamIfMemberOrThrow(
        teamId,
        actor._id.toString(),
      );
      const squadRole = this.teamsService.getSquadRoleForUser(
        team,
        actor._id.toString(),
      );
      if (!isSquadOfficerRole(squadRole)) {
        throw new ForbiddenException(
          'Only squad ranks R4 and R5 can moderate team chat messages',
        );
      }
      return;
    }
    throw new ForbiddenException('Not allowed to moderate this message');
  }

  /** Pin/unpin in `pt:<teamId>` team chat rooms — squad R4/R5 only. */
  private async assertMayPinInTeamRoom(
    actor: UserDocument,
    room: Pick<ChatRoom, 'allianceId'>,
  ): Promise<void> {
    const teamId = parsePlayerTeamIdFromChatScope(room.allianceId);
    if (!teamId) {
      throw new ForbiddenException(
        'Pinning is only available in team chat rooms',
      );
    }
    const team = await this.teamsService.getTeamIfMemberOrThrow(
      teamId,
      actor._id.toString(),
    );
    const squadRole = this.teamsService.getSquadRoleForUser(
      team,
      actor._id.toString(),
    );
    if (!isSquadOfficerRole(squadRole)) {
      throw new ForbiddenException(
        'Only squad ranks R4 and R5 can pin messages',
      );
    }
  }

  private roomPinChangedPayload(
    roomId: string,
    room: Pick<
      ChatRoom,
      'pinnedMessageId' | 'pinnedAt' | 'pinnedByUserId'
    >,
    pinnedMessage: PinnedMessagePreview | null,
    pinnedMessages: PinnedMessagePreview[],
  ): ChatRoomPinChangedPayload {
    return {
      roomId,
      pinnedMessageId: room.pinnedMessageId?.toString() ?? null,
      pinnedAt: room.pinnedAt?.toISOString() ?? null,
      pinnedByUserId: room.pinnedByUserId ?? null,
      pinnedMessage,
      pinnedMessages,
    };
  }

  private async buildPinnedMessagesFromHistory(
    history: PinHistoryEntry[],
  ): Promise<PinnedMessagePreview[]> {
    if (history.length === 0) return [];
    const ids = history.map((h) => h.messageId);
    const msgs = await this.messageModel
      .find({ _id: { $in: ids } })
      .lean()
      .exec();
    const byId = new Map(
      msgs.map((m) => [String(m._id), buildPinnedPreviewFromChatMessage(m)]),
    );
    const actorIds = [
      ...new Set(history.map((h) => h.pinnedByUserId.trim()).filter(Boolean)),
    ];
    const actorNames = await this.resolvePinnedByUsernames(actorIds);
    const out: PinnedMessagePreview[] = [];
    for (const entry of history) {
      const msgId = entry.messageId.toString();
      const preview =
        byId.get(msgId) ??
        buildStubPinnedPreview(
          msgId,
          actorNames.get(entry.pinnedByUserId.trim()) ?? null,
        );
      out.push(
        enrichPinnedPreview(
          preview,
          actorNames.get(entry.pinnedByUserId.trim()) ?? null,
        ),
      );
    }
    return out;
  }

  private async roomPinPayloadFromDoc(
    roomId: string,
    room: ChatRoomDocument,
  ): Promise<ChatRoomPinChangedPayload> {
    const history = this.chatRoomsService.pinHistoryForRoom(room);
    const pinnedMessages = await this.buildPinnedMessagesFromHistory(history);
    const activeId = room.pinnedMessageId?.toString() ?? null;
    const pinnedMessage =
      pinnedMessages.find((p) => p.id === activeId) ?? pinnedMessages[0] ?? null;
    return this.roomPinChangedPayload(
      roomId,
      room,
      pinnedMessage,
      pinnedMessages,
    );
  }

  private async pinnedPreviewForMessageId(
    messageId: Types.ObjectId | string | null | undefined,
  ): Promise<PinnedMessagePreview | null> {
    const id = messageId?.toString()?.trim();
    if (!id || !Types.ObjectId.isValid(id)) {
      return null;
    }
    const msg = await this.messageModel.findById(id).lean().exec();
    if (!msg) {
      return null;
    }
    return buildPinnedPreviewFromChatMessage(msg);
  }

  async buildPinnedPreviewsForRooms<
    T extends {
      _id: Types.ObjectId;
      pinnedMessageId?: Types.ObjectId | null;
      pinnedByUserId?: string | null;
    },
  >(rooms: T[]): Promise<Map<string, PinnedMessagePreview | null>> {
    const pinIds = [
      ...new Set(
        rooms
          .map((r) => r.pinnedMessageId?.toString())
          .filter((id): id is string => !!id && Types.ObjectId.isValid(id)),
      ),
    ];
    const out = new Map<string, PinnedMessagePreview | null>();
    if (pinIds.length === 0) {
      for (const room of rooms) {
        out.set(room._id.toString(), null);
      }
      return out;
    }
    const msgs = await this.messageModel
      .find({ _id: { $in: pinIds.map((id) => new Types.ObjectId(id)) } })
      .lean()
      .exec();
    const byMsgId = new Map(
      msgs.map((m) => [
        String(m._id),
        buildPinnedPreviewFromChatMessage(m),
      ]),
    );
    const pinUserIds = [
      ...new Set(
        rooms
          .map((r) => r.pinnedByUserId?.trim())
          .filter((id): id is string => !!id),
      ),
    ];
    const pinUsernames = await this.resolvePinnedByUsernames(pinUserIds);
    for (const room of rooms) {
      const rid = room._id.toString();
      const pinId = room.pinnedMessageId?.toString();
      let preview = pinId ? (byMsgId.get(pinId) ?? null) : null;
      if (preview && room.pinnedByUserId) {
        preview = enrichPinnedPreview(
          preview,
          pinUsernames.get(room.pinnedByUserId.trim()) ?? null,
        );
      }
      out.set(rid, preview);
    }
    return out;
  }

  private async resolvePinnedByUsernames(
    userIds: string[],
  ): Promise<Map<string, string>> {
    const out = new Map<string, string>();
    const unique = [...new Set(userIds.map((id) => id.trim()).filter(Boolean))];
    await Promise.all(
      unique.map(async (id) => {
        const user = await this.usersService.findById(id);
        const name = (user?.username ?? user?.email ?? '').trim();
        if (name) out.set(id, name);
      }),
    );
    return out;
  }

  attachPinStateToRoom<T extends ChatRoom>(
    room: T,
    pinnedMessage: PinnedMessagePreview | null,
    pinnedMessages: PinnedMessagePreview[] = pinnedMessage
      ? [pinnedMessage]
      : [],
  ): T & ChatRoomWithPinState {
    return {
      ...room,
      pinnedMessageId: room.pinnedMessageId?.toString() ?? null,
      pinnedAt: room.pinnedAt?.toISOString() ?? null,
      pinnedByUserId: room.pinnedByUserId ?? null,
      pinnedMessage,
      pinnedMessages,
    };
  }

  private async buildPinnedMessagesForRoomsBatch<
    T extends {
      _id: Types.ObjectId;
      pinHistory?: PinHistoryEntry[];
    },
  >(rooms: T[]): Promise<Map<string, PinnedMessagePreview[]>> {
    const histories = rooms.map((doc) => ({
      roomId: doc._id.toString(),
      history: normalizePinHistory(ensurePinHistoryMigrated(doc)),
    }));
    const allMessageIds = [
      ...new Set(
        histories.flatMap((h) =>
          h.history.map((entry) => entry.messageId.toString()),
        ),
      ),
    ].filter((id) => Types.ObjectId.isValid(id));
    const msgs =
      allMessageIds.length === 0
        ? []
        : await this.messageModel
            .find({
              _id: { $in: allMessageIds.map((id) => new Types.ObjectId(id)) },
            })
            .lean()
            .exec();
    const byId = new Map(
      msgs.map((m) => [String(m._id), buildPinnedPreviewFromChatMessage(m)]),
    );
    const actorIds = [
      ...new Set(
        histories.flatMap((h) =>
          h.history.map((entry) => entry.pinnedByUserId.trim()).filter(Boolean),
        ),
      ),
    ];
    const actorNames = await this.resolvePinnedByUsernames(actorIds);
    const out = new Map<string, PinnedMessagePreview[]>();
    for (const { roomId, history } of histories) {
      const previews: PinnedMessagePreview[] = [];
      for (const entry of history) {
        const msgId = entry.messageId.toString();
        const preview =
          byId.get(msgId) ??
          buildStubPinnedPreview(
            msgId,
            actorNames.get(entry.pinnedByUserId.trim()) ?? null,
          );
        previews.push(
          enrichPinnedPreview(
            preview,
            actorNames.get(entry.pinnedByUserId.trim()) ?? null,
          ),
        );
      }
      out.set(roomId, previews);
    }
    return out;
  }

  async attachPinStateToRooms<
    T extends {
      _id: Types.ObjectId;
      pinnedMessageId?: Types.ObjectId | null;
      pinnedAt?: Date | null;
      pinnedByUserId?: string | null;
      pinHistory?: PinHistoryEntry[];
    },
  >(rooms: T[]): Promise<(T & ChatRoomWithPinState)[]> {
    const startedAt = Date.now();
    const previews = await this.buildPinnedPreviewsForRooms(rooms);
    const historyMap = await this.buildPinnedMessagesForRoomsBatch(rooms);
    const result = rooms.map((room) => {
      const rid = room._id.toString();
      const activePreview = previews.get(rid) ?? null;
      const pinnedMessages =
        historyMap.get(rid) ??
        (activePreview ? [activePreview] : []);
      return this.attachPinStateToRoom(
        room as T & ChatRoom,
        activePreview,
        pinnedMessages,
      );
    });
    const elapsedMs = Date.now() - startedAt;
    if (elapsedMs > 200) {
      console.log(
        `[PerfDiag] attachPinStateToRooms rooms=${rooms.length} ms=${elapsedMs}`,
      );
    }
    return result;
  }

  async setRoomPinnedMessage(
    userId: string,
    roomId: string,
    messageId: string | null,
  ): Promise<{
    room: ChatRoomDocument & ChatRoomWithPinState;
    pinChanged: ChatRoomPinChangedPayload;
  }> {
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    await this.assertRoomForUser(userId, roomId);
    const roomBefore = await this.chatRoomsService.findById(roomId);
    if (!roomBefore || roomBefore.archivedAt) {
      throw new NotFoundException('Room not found');
    }
    await this.assertMayPinInTeamRoom(actor, roomBefore);

    const trimmed = messageId?.trim() ?? '';
    if (!trimmed) {
      const updated = await this.chatRoomsService.setPinnedMessage(roomId, {
        messageId: null,
        pinnedAt: null,
        pinnedByUserId: null,
      });
      if (!updated) {
        throw new NotFoundException('Room not found');
      }
      const pinChanged = await this.roomPinPayloadFromDoc(roomId, updated);
      const room = this.attachPinStateToRoom(
        updated,
        null,
        pinChanged.pinnedMessages,
      );
      await this.writeChatPinAudit(
        roomBefore,
        roomId,
        userId,
        'unpin_all',
        null,
      );
      return { room, pinChanged };
    }

    if (!Types.ObjectId.isValid(trimmed)) {
      throw new BadRequestException('Invalid message id');
    }
    const msg = await this.messageModel.findById(trimmed).lean().exec();
    if (!msg || msg.roomId.toString() !== roomBefore._id.toString()) {
      throw new NotFoundException('Message not found');
    }

    const msgOid = new Types.ObjectId(trimmed);
    const updated = await this.chatRoomsService.setPinnedMessage(roomId, {
      messageId: msgOid,
      pinnedAt: new Date(),
      pinnedByUserId: userId,
    });
    if (!updated) {
      throw new NotFoundException('Room not found');
    }
    const pinChanged = await this.roomPinPayloadFromDoc(roomId, updated);
    const room = this.attachPinStateToRoom(
      updated,
      pinChanged.pinnedMessage,
      pinChanged.pinnedMessages,
    );
    await this.writeChatPinAudit(roomBefore, roomId, userId, 'pin', trimmed);
    return { room, pinChanged };
  }

  async unpinOneRoomMessage(
    userId: string,
    roomId: string,
    messageId: string,
  ): Promise<{
    room: ChatRoomDocument & ChatRoomWithPinState;
    pinChanged: ChatRoomPinChangedPayload;
  }> {
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    await this.assertRoomForUser(userId, roomId);
    const roomBefore = await this.chatRoomsService.findById(roomId);
    if (!roomBefore || roomBefore.archivedAt) {
      throw new NotFoundException('Room not found');
    }
    await this.assertMayPinInTeamRoom(actor, roomBefore);
    const trimmed = messageId?.trim() ?? '';
    if (!trimmed || !Types.ObjectId.isValid(trimmed)) {
      throw new BadRequestException('Invalid message id');
    }
    const updated = await this.chatRoomsService.unpinOneMessage(roomId, trimmed);
    if (!updated) {
      throw new NotFoundException('Room not found');
    }
    const pinChanged = await this.roomPinPayloadFromDoc(roomId, updated);
    const room = this.attachPinStateToRoom(
      updated,
      pinChanged.pinnedMessage,
      pinChanged.pinnedMessages,
    );
    await this.writeChatPinAudit(roomBefore, roomId, userId, 'unpin', trimmed);
    return { room, pinChanged };
  }

  private async writeChatPinAudit(
    room: { allianceId?: string | null },
    roomId: string,
    userId: string,
    action: 'pin' | 'unpin' | 'unpin_all',
    messageId: string | null,
  ): Promise<void> {
    const teamId = parsePlayerTeamIdFromChatScope(room.allianceId ?? '');
    if (!teamId) return;
    await this.pinAudit.append({
      teamId,
      scope: 'chat',
      scopeId: roomId,
      messageId,
      action,
      userId,
    });
  }

  private async assertMayAccessMessageRoom(
    userId: string,
    message: MessageLean,
  ): Promise<void> {
    const actor = await this.usersService.findById(userId);
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    if (
      !userMayAccessChatRoom(actor, {
        allianceId: message.allianceId,
        archivedAt: null,
      })
    ) {
      throw new ForbiddenException('Room is not available for your alliance');
    }
  }

  private async assertRoomForUser(
    userId: string,
    roomId: string,
  ): Promise<{ allianceId: string; roomObjectId: Types.ObjectId }> {
    const user = await this.usersService.findById(userId);
    if (!user) {
      throw new ForbiddenException('User not found');
    }
    const room = await this.chatRoomsService.findById(roomId);
    if (!room || room.archivedAt) {
      throw new ForbiddenException('Room not found');
    }
    if (!userMayAccessChatRoom(user, room)) {
      throw new ForbiddenException('Room is not available for your alliance');
    }
    return {
      allianceId: room.allianceId,
      roomObjectId: room._id,
    };
  }

  private asIdString(
    id: Types.ObjectId | string | null | undefined,
  ): string | null {
    if (!id) return null;
    return typeof id === 'string' ? id : id.toString();
  }

  private toIso(date: Date | null | undefined): string | null {
    return date ? date.toISOString() : null;
  }

  /** Telegram-style: omit edited marker unless text was changed after send. */
  private effectiveEditedAtIso(message: MessageLean): string | null {
    const edited = message.editedAt;
    if (!edited) return null;
    const created = message.createdAt;
    if (!created) return this.toIso(edited);
    if (edited.getTime() - created.getTime() < 2_000) return null;
    return this.toIso(edited);
  }

  private resolveStoredSenderServerNumber(
    message: MessageLean,
    senderServerNumberMap?: Map<string, number | null>,
  ): number | null {
    const stored = message.senderServerNumber ?? null;
    if (stored != null && stored >= 1) return stored;
    return senderServerNumberMap?.get(message.senderId) ?? null;
  }

  private serializeReplyPreview(
    message: MessageLean,
    senderTeamTagMap?: Map<string, string | null>,
    senderServerNumberMap?: Map<string, number | null>,
  ): ChatMessageReplyPreview {
    const senderTeamTag =
      message.senderTeamTag ?? senderTeamTagMap?.get(message.senderId) ?? null;
    const senderServerNumber = this.resolveStoredSenderServerNumber(
      message,
      senderServerNumberMap,
    );
    return {
      _id: this.asIdString(message._id)!,
      senderId: message.senderId,
      senderUsername: message.senderUsername,
      senderRole: message.senderRole,
      senderTeamTag,
      senderServerNumber,
      text: message.deletedAt ? '' : message.text,
      createdAt: this.toIso(message.createdAt),
      deletedAt: this.toIso(message.deletedAt),
    };
  }

  private serializeMessage(
    message: MessageLean,
    replyMap?: Map<string, MessageLean>,
    senderTelegramMap?: Map<string, string | null>,
    senderTeamTagMap?: Map<string, string | null>,
    viewerUserId?: string,
    senderServerNumberMap?: Map<string, number | null>,
  ): ChatMessageView {
    const replyToMessageId = this.asIdString(message.replyToMessageId);
    const replyTarget = replyToMessageId
      ? replyMap?.get(replyToMessageId)
      : null;
    const senderTeamTag =
      message.senderTeamTag ?? senderTeamTagMap?.get(message.senderId) ?? null;
    const attachments = message.deletedAt
      ? []
      : (message.attachments ?? []).map((a) => ({
          kind: a.kind,
          url: `/chat/attachments/${a.fileId.toString()}`,
          mimeType: a.mimeType,
          size: a.size,
          filename: a.filename ?? null,
        }));
    const forwardedFrom = message.forwardedFrom
      ? {
          messageId: message.forwardedFrom.messageId.toString(),
          senderId: message.forwardedFrom.senderId,
          senderUsername: message.forwardedFrom.senderUsername,
          senderRole: message.forwardedFrom.senderRole,
          senderTeamTag: message.forwardedFrom.senderTeamTag ?? null,
          senderServerNumber:
            message.forwardedFrom.senderServerNumber ??
            senderServerNumberMap?.get(message.forwardedFrom.senderId) ??
            null,
        }
      : null;
    const reactions = (message.reactions ?? [])
      .filter((r) => r.emoji && (r.userIds?.length ?? 0) > 0)
      .map((r) => ({
        emoji: r.emoji,
        count: r.userIds.length,
        reactedByMe: viewerUserId ? r.userIds.includes(viewerUserId) : false,
      }));
    return {
      _id: this.asIdString(message._id)!,
      allianceId: message.allianceId,
      roomId: this.asIdString(message.roomId)!,
      senderId: message.senderId,
      senderUsername: message.senderUsername,
      senderRole: message.senderRole,
      senderTeamTag,
      senderServerNumber: this.resolveStoredSenderServerNumber(
        message,
        senderServerNumberMap,
      ),
      senderTelegramUsername: senderTelegramMap?.get(message.senderId) ?? null,
      text: message.deletedAt ? '' : message.text,
      editedAt: this.effectiveEditedAtIso(message),
      forwardedFrom: message.deletedAt ? null : forwardedFrom,
      reactions: message.deletedAt ? [] : reactions,
      attachments,
      createdAt: this.toIso(message.createdAt),
      updatedAt: this.toIso(message.updatedAt),
      replyToMessageId,
      replyTo: replyTarget
        ? this.serializeReplyPreview(
            replyTarget,
            senderTeamTagMap,
            senderServerNumberMap,
          )
        : null,
      deletedAt: this.toIso(message.deletedAt),
      deletedByUserId: message.deletedByUserId ?? null,
    };
  }

  private async loadReplyMap(
    messages: MessageLean[],
  ): Promise<Map<string, MessageLean>> {
    const replyIds = [
      ...new Set(
        messages
          .map((message) => this.asIdString(message.replyToMessageId))
          .filter((value): value is string => Boolean(value)),
      ),
    ];
    if (replyIds.length == 0) {
      return new Map();
    }
    const replyDocs = await this.messageModel
      .find({ _id: { $in: replyIds.map((id) => new Types.ObjectId(id)) } })
      .lean<MessageLean[]>()
      .exec();
    return new Map(replyDocs.map((doc) => [this.asIdString(doc._id)!, doc]));
  }

  private async enrichMessages(
    messages: MessageLean[],
    viewerUserId: string,
  ): Promise<ChatMessageView[]> {
    const replyMap = await this.loadReplyMap(messages);
    const replySenderIds: string[] = [];
    for (const m of messages) {
      const rid = this.asIdString(m.replyToMessageId);
      if (rid) {
        const target = replyMap.get(rid);
        if (target) replySenderIds.push(target.senderId);
      }
    }
    const forwardSenderIds = messages
      .map((m) => m.forwardedFrom?.senderId)
      .filter((v): v is string => Boolean(v));
    const senderIds = [
      ...new Set([
        ...messages.map((m) => m.senderId),
        ...replySenderIds,
        ...forwardSenderIds,
      ]),
    ];
    const squadRoleMap =
      await this.teamsService.resolveSquadRolesByUserIds(senderIds);
    const senderTelegramMap =
      await this.usersService.findTelegramUsernamesByIds(senderIds);
    const senderTeamTagMap =
      await this.usersService.findTeamTagsByIds(senderIds);
    const senderServerNumberMap =
      await this.gameIdentities.buildSenderServerNumberMap(senderIds);
    const replyMapWithRoles = new Map(
      [...replyMap.entries()].map(([id, doc]) => [
        id,
        this.withSquadDisplayRoles(doc, squadRoleMap),
      ]),
    );
    return messages.map((message) =>
      this.serializeMessage(
        this.withSquadDisplayRoles(message, squadRoleMap),
        replyMapWithRoles,
        senderTelegramMap,
        senderTeamTagMap,
        viewerUserId,
        senderServerNumberMap,
      ),
    );
  }

  private async getReplyTarget(
    allianceId: string,
    roomObjectId: Types.ObjectId,
    replyToMessageId?: string,
  ): Promise<MessageLean | null> {
    const replyId = replyToMessageId?.trim();
    if (!replyId) return null;
    if (!Types.ObjectId.isValid(replyId)) {
      throw new BadRequestException('Invalid reply target');
    }
    const replyTarget = await this.messageModel
      .findOne({
        _id: new Types.ObjectId(replyId),
        allianceId,
        roomId: roomObjectId,
      })
      .lean<MessageLean | null>()
      .exec();
    if (!replyTarget || replyTarget.deletedAt) {
      throw new BadRequestException('Reply target is unavailable');
    }
    return replyTarget;
  }

  async createMessage(input: {
    text?: string;
    author: MessageAuthor;
    roomId: string;
    replyToMessageId?: string;
    attachments?: MessageAttachment[];
  }): Promise<ChatMessageView> {
    const authorUser = await this.usersService.findById(input.author.userId);
    if (
      !authorUser ||
      this.usersService.effectiveMembership(authorUser) !==
        TeamMembershipStatus.ACTIVE
    ) {
      throw new ForbiddenException('Chat is not available for this account');
    }
    this.assertNotMuted(authorUser);

    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      input.author.userId,
      input.roomId,
    );
    this.assertGlobalChatBrandingIfNeeded(allianceId, authorUser);
    const replyTarget = await this.getReplyTarget(
      allianceId,
      roomObjectId,
      input.replyToMessageId,
    );

    const trimmedText = (input.text ?? '').trim();
    const hasAttachments = (input.attachments?.length ?? 0) > 0;
    if (!trimmedText && !hasAttachments) {
      throw new BadRequestException(
        'Message must include non-empty text or at least one attachment',
      );
    }
    assertStickerPayload(trimmedText);
    await this.stickerAccess.assertUserMaySendStickerMessage(
      authorUser,
      trimmedText,
    );

    const squadRoleMap = await this.teamsService.resolveSquadRolesByUserIds([
      input.author.userId,
    ]);
    const senderSquadRole =
      squadRoleMap.get(input.author.userId) ?? PlayerTeamMemberRole.R1;

    const created = await this.messageModel.create({
      allianceId,
      roomId: roomObjectId,
      text: trimmedText,
      attachments: input.attachments ?? [],
      senderId: input.author.userId,
      senderUsername: this.gameIdentities.resolveSenderUsername(authorUser),
      senderRole: senderSquadRole,
      senderTeamTag: authorUser.teamTag ?? null,
      senderServerNumber:
        this.gameIdentities.resolveSenderServerNumber(authorUser),
      replyToMessageId: replyTarget?._id ?? null,
      deletedAt: null,
      deletedByUserId: null,
      editedAt: null,
      reactions: [],
    });
    return this.viewMessageForUser(
      created.toObject<MessageLean>(),
      input.author.userId,
    );
  }

  /** R5 admin: read room history without membership check. */
  async getRecentMessagesForAdmin(
    roomId: string,
    options?: { limit?: number; before?: string },
  ) {
    if (!Types.ObjectId.isValid(roomId)) {
      throw new BadRequestException('Invalid roomId');
    }
    const room = await this.chatRoomsService.findById(roomId);
    if (!room || room.archivedAt) {
      throw new NotFoundException('Room not found');
    }
    const rawLimit = options?.limit ?? 50;
    const limit = Math.min(200, Math.max(1, Math.floor(rawLimit)));
    const filter: Record<string, unknown> = {
      allianceId: room.allianceId,
      roomId: room._id,
      deletedAt: null,
    };
    const before = options?.before?.trim();
    if (before) {
      if (!Types.ObjectId.isValid(before)) {
        throw new BadRequestException('Invalid before cursor');
      }
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    const messages = await this.messageModel
      .find(filter)
      /**
       * IMPORTANT: cursor pagination uses `_id < before`, so the sort MUST be based on `_id`.
       * Sorting by `createdAt` here can produce gaps between pages (inconsistent cursor).
       */
      .sort({ _id: -1 })
      .limit(limit)
      .lean<MessageLean[]>()
      .exec();
    return this.enrichMessages(messages, '');
  }

  async getRecentMessages(
    userId: string,
    roomId: string,
    options?: { limit?: number; before?: string },
  ) {
    const rawLimit = options?.limit ?? 30;
    const limit = Math.min(100, Math.max(1, Math.floor(rawLimit)));
    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      userId,
      roomId,
    );
    const filter: Record<string, unknown> = {
      allianceId,
      roomId: roomObjectId,
      deletedAt: null,
    };
    const hiddenBefore = await this.getHiddenBeforeMessageId(
      userId,
      roomObjectId,
    );
    const idBounds = this.messageIdBoundsFilter(
      hiddenBefore,
      options?.before?.trim(),
    );
    if (idBounds) {
      filter._id = idBounds;
    }
    const messages = await this.messageModel
      .find(filter)
      /**
       * IMPORTANT: cursor pagination uses `_id < before`, so the sort MUST be based on `_id`.
       * Sorting by `createdAt` here can produce gaps between pages (inconsistent cursor).
       */
      .sort({ _id: -1 })
      .limit(limit)
      .lean<MessageLean[]>()
      .exec();
    return this.enrichMessages(messages, userId);
  }

  async editMessage(
    userId: string,
    messageId: string,
    text: string,
  ): Promise<{
    message: ChatMessageView;
    pinChanged: ChatRoomPinChangedPayload | null;
  }> {
    if (!Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) throw new ForbiddenException('User not found');
    this.assertNotMuted(actor);
    const message = await this.messageModel.findById(messageId).exec();
    if (!message) throw new NotFoundException('Message not found');
    const lean = message.toObject<MessageLean>();
    await this.assertMayAccessMessageRoom(userId, lean);
    await this.assertMayModerateOthersMessage(actor, lean);
    const trimmed = text.trim();
    if (!trimmed) throw new BadRequestException('Text is required');
    assertStickerPayload(trimmed);
    await this.stickerAccess.assertUserMaySendStickerMessage(actor, trimmed);
    message.text = trimmed;
    message.editedAt = new Date();
    await message.save();
    const leanSaved = message.toObject<MessageLean>();
    const pinChanged = await this.pinChangedIfMessageInHistory(leanSaved);
    const view = await this.viewMessageForUser(leanSaved, userId);
    return { message: view, pinChanged };
  }

  private async pinChangedIfMessageInHistory(
    message: MessageLean,
  ): Promise<ChatRoomPinChangedPayload | null> {
    const msgId = message._id.toString();
    const roomId = message.roomId.toString();
    const room = await this.chatRoomsService.findById(roomId);
    if (!room) return null;
    const history = this.chatRoomsService.pinHistoryForRoom(room);
    const isPinned = history.some((h) => h.messageId.toString() === msgId);
    if (!isPinned) return null;
    return this.roomPinPayloadFromDoc(roomId, room);
  }

  async toggleReaction(
    userId: string,
    messageId: string,
    emoji: string,
  ): Promise<ChatMessageView> {
    if (!Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) throw new ForbiddenException('User not found');
    this.assertNotMuted(actor);
    const trimmed = emoji.trim();
    if (!trimmed) throw new BadRequestException('emoji is required');
    const message = await this.messageModel.findById(messageId).exec();
    if (!message) throw new NotFoundException('Message not found');
    await this.assertMayAccessMessageRoom(
      userId,
      message.toObject<MessageLean>(),
    );
    const list = (message.reactions ?? []) as {
      emoji: string;
      userIds: string[];
    }[];
    const r = list.find((x) => x.emoji === trimmed);
    if (!r) {
      list.push({ emoji: trimmed, userIds: [userId] });
    } else {
      const idx = r.userIds.indexOf(userId);
      if (idx >= 0) r.userIds.splice(idx, 1);
      else r.userIds.push(userId);
    }
    message.reactions = list.filter((x) => x.userIds.length > 0) as any;
    await message.save();
    return this.viewMessageForUser(message.toObject<MessageLean>(), userId);
  }

  /** Raw reactions for neutral `message:reaction` socket fanout. */
  async getReactionBroadcastPayload(messageId: string) {
    if (!Types.ObjectId.isValid(messageId)) {
      return null;
    }
    const message = await this.messageModel
      .findById(messageId)
      .lean<MessageLean>();
    if (!message) {
      return null;
    }
    const roomId = this.asIdString(message.roomId);
    if (!roomId) {
      return null;
    }
    return buildMessageReactionBroadcastPayload({
      messageId: message._id.toString(),
      roomId,
      reactions: message.reactions as
        | { emoji: string; userIds: string[] }[]
        | undefined,
    });
  }

  async forwardMessage(
    userId: string,
    roomId: string,
    sourceMessageId: string,
  ): Promise<ChatMessageView> {
    if (!Types.ObjectId.isValid(sourceMessageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) throw new ForbiddenException('User not found');
    this.assertNotMuted(actor);
    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      userId,
      roomId,
    );
    this.assertGlobalChatBrandingIfNeeded(allianceId, actor);
    const source = await this.messageModel
      .findOne({
        _id: new Types.ObjectId(sourceMessageId),
        allianceId,
        roomId: roomObjectId,
        deletedAt: null,
      })
      .lean<MessageLean | null>()
      .exec();
    if (!source) throw new NotFoundException('Message not found');
    const fwdText = (source.text ?? '').trim();
    assertStickerPayload(fwdText);
    await this.stickerAccess.assertUserMaySendStickerMessage(actor, fwdText);
    const squadRoleMap = await this.teamsService.resolveSquadRolesByUserIds([
      userId,
      source.senderId,
    ]);
    const actorSquadRole = squadRoleMap.get(userId) ?? PlayerTeamMemberRole.R1;
    const sourceSquadRole =
      squadRoleMap.get(source.senderId) ?? PlayerTeamMemberRole.R1;
    const created = await this.messageModel.create({
      allianceId,
      roomId: roomObjectId,
      text: fwdText,
      attachments: source.attachments ?? [],
      senderId: userId,
      senderUsername: this.gameIdentities.resolveSenderUsername(actor),
      senderRole: actorSquadRole,
      senderTeamTag: actor.teamTag ?? null,
      senderServerNumber: this.gameIdentities.resolveSenderServerNumber(actor),
      replyToMessageId: null,
      deletedAt: null,
      deletedByUserId: null,
      forwardedFrom: {
        messageId: new Types.ObjectId(sourceMessageId),
        senderId: source.senderId,
        senderUsername: source.senderUsername,
        senderRole: sourceSquadRole,
        senderTeamTag: source.senderTeamTag ?? null,
        senderServerNumber: source.senderServerNumber ?? null,
      },
      editedAt: null,
      reactions: [],
    });
    return this.viewMessageForUser(created.toObject<MessageLean>(), userId);
  }

  private async readStatesByRoomIds(
    userId: string,
    roomIds: string[],
  ): Promise<Map<string, string>> {
    const valid = roomIds
      .filter((id) => Types.ObjectId.isValid(id))
      .map((id) => new Types.ObjectId(id));
    if (valid.length === 0) return new Map();

    const readStates = await this.chatReadStateModel
      .find({ userId, roomId: { $in: valid } })
      .lean()
      .exec();
    return new Map(
      readStates.map((r) => [r.roomId.toString(), r.lastReadMessageId]),
    );
  }

  async getLastReadMessageIdsByRoomIds(
    userId: string,
    roomIds: string[],
  ): Promise<Map<string, string>> {
    return this.readStatesByRoomIds(userId, roomIds);
  }

  async countUnreadByRoomIds(
    userId: string,
    roomIds: string[],
  ): Promise<Map<string, number>> {
    const out = new Map<string, number>();
    const valid = roomIds
      .filter((id) => Types.ObjectId.isValid(id))
      .map((id) => new Types.ObjectId(id));
    if (valid.length === 0) return out;

    for (const oid of valid) {
      out.set(oid.toString(), 0);
    }

    const rows = await this.messageModel
      .aggregate<{ _id: Types.ObjectId; count: number }>([
        {
          $match: {
            roomId: { $in: valid },
            deletedAt: null,
            senderId: { $ne: userId },
          },
        },
        {
          $lookup: {
            from: this.chatReadStateModel.collection.name,
            let: { rid: '$roomId' },
            pipeline: [
              {
                $match: {
                  $expr: {
                    $and: [
                      { $eq: ['$roomId', '$$rid'] },
                      { $eq: ['$userId', userId] },
                    ],
                  },
                },
              },
              {
                $project: {
                  lastReadMessageId: 1,
                  hiddenBeforeMessageId: 1,
                  _id: 0,
                },
              },
              { $limit: 1 },
            ],
            as: 'readState',
          },
        },
        {
          $addFields: {
            lastReadOid: {
              $let: {
                vars: {
                  raw: {
                    $arrayElemAt: ['$readState.lastReadMessageId', 0],
                  },
                },
                in: {
                  $cond: [
                    {
                      $and: [{ $ne: ['$$raw', null] }, { $ne: ['$$raw', ''] }],
                    },
                    { $toObjectId: '$$raw' },
                    null,
                  ],
                },
              },
            },
            hiddenBeforeOid: {
              $let: {
                vars: {
                  raw: {
                    $arrayElemAt: ['$readState.hiddenBeforeMessageId', 0],
                  },
                },
                in: {
                  $cond: [
                    {
                      $and: [{ $ne: ['$$raw', null] }, { $ne: ['$$raw', ''] }],
                    },
                    { $toObjectId: '$$raw' },
                    null,
                  ],
                },
              },
            },
          },
        },
        {
          $match: {
            $expr: {
              $and: [
                {
                  $or: [
                    { $eq: ['$hiddenBeforeOid', null] },
                    { $gt: ['$_id', '$hiddenBeforeOid'] },
                  ],
                },
                {
                  $or: [
                    { $eq: ['$lastReadOid', null] },
                    { $gt: ['$_id', '$lastReadOid'] },
                  ],
                },
              ],
            },
          },
        },
        { $group: { _id: '$roomId', count: { $sum: 1 } } },
      ])
      .exec();

    for (const row of rows) {
      out.set(row._id.toString(), row.count);
    }
    return out;
  }

  private messageIdBoundsFilter(
    hiddenBefore?: string | null,
    before?: string | null,
  ): Record<string, Types.ObjectId> | undefined {
    const hidden = hiddenBefore?.trim();
    const beforeId = before?.trim();
    const bounds: Record<string, Types.ObjectId> = {};
    if (hidden) {
      if (!Types.ObjectId.isValid(hidden)) {
        throw new BadRequestException('Invalid hiddenBeforeMessageId');
      }
      bounds.$gt = new Types.ObjectId(hidden);
    }
    if (beforeId) {
      if (!Types.ObjectId.isValid(beforeId)) {
        throw new BadRequestException('Invalid before cursor');
      }
      bounds.$lt = new Types.ObjectId(beforeId);
    }
    return Object.keys(bounds).length > 0 ? bounds : undefined;
  }

  private async getHiddenBeforeMessageId(
    userId: string,
    roomObjectId: Types.ObjectId,
  ): Promise<string | null> {
    const row = await this.chatReadStateModel
      .findOne({ roomId: roomObjectId, userId })
      .select('hiddenBeforeMessageId')
      .lean()
      .exec();
    const hidden = row?.hiddenBeforeMessageId?.trim();
    return hidden && Types.ObjectId.isValid(hidden) ? hidden : null;
  }

  /**
   * Hide all current messages in a room for this user only (DB rows are kept).
   */
  async clearRoomHistoryForUser(
    userId: string,
    roomId: string,
  ): Promise<{
    roomId: string;
    hiddenBeforeMessageId: string | null;
    lastReadMessageId: string | null;
    unreadCount: number;
  }> {
    if (!Types.ObjectId.isValid(roomId)) {
      throw new BadRequestException('Invalid room id');
    }
    await this.assertUserMayUseChat(userId);
    const { roomObjectId } = await this.assertRoomForUser(userId, roomId);
    const newest = await this.messageModel
      .findOne({ roomId: roomObjectId, deletedAt: null })
      .sort({ _id: -1 })
      .select('_id')
      .lean()
      .exec();
    if (!newest?._id) {
      return {
        roomId,
        hiddenBeforeMessageId: null,
        lastReadMessageId: null,
        unreadCount: 0,
      };
    }
    const watermark = newest._id.toString();
    const existing = await this.chatReadStateModel
      .findOne({ roomId: roomObjectId, userId })
      .lean()
      .exec();
    const prevRead = existing?.lastReadMessageId?.trim();
    const prevHidden = existing?.hiddenBeforeMessageId?.trim();
    const watermarkOid = new Types.ObjectId(watermark);
    let lastReadMessageId = watermark;
    if (prevRead && Types.ObjectId.isValid(prevRead)) {
      const prevOid = new Types.ObjectId(prevRead);
      lastReadMessageId =
        watermarkOid >= prevOid ? watermark : prevRead;
    }
    let hiddenBeforeMessageId = watermark;
    if (prevHidden && Types.ObjectId.isValid(prevHidden)) {
      const prevHiddenOid = new Types.ObjectId(prevHidden);
      hiddenBeforeMessageId =
        watermarkOid >= prevHiddenOid ? watermark : prevHidden;
    }
    await this.chatReadStateModel
      .updateOne(
        { roomId: roomObjectId, userId },
        {
          $set: {
            lastReadMessageId,
            hiddenBeforeMessageId,
          },
        },
        { upsert: true },
      )
      .exec();
    const unreadMap = await this.countUnreadByRoomIds(userId, [roomId]);
    return {
      roomId,
      hiddenBeforeMessageId,
      lastReadMessageId,
      unreadCount: unreadMap.get(roomId) ?? 0,
    };
  }

  async getPeerReadUptoMessageId(
    userId: string,
    roomId: string,
  ): Promise<string | null> {
    if (!Types.ObjectId.isValid(roomId)) {
      throw new BadRequestException('Invalid room id');
    }
    await this.assertUserMayUseChat(userId);
    const { roomObjectId } = await this.assertRoomForUser(userId, roomId);
    const rows = await this.chatReadStateModel
      .find({ roomId: roomObjectId, userId: { $ne: userId } })
      .select('lastReadMessageId')
      .lean()
      .exec();
    let max: string | null = null;
    for (const row of rows) {
      const id = row.lastReadMessageId?.trim();
      if (!id || !Types.ObjectId.isValid(id)) continue;
      if (!max || new Types.ObjectId(id) > new Types.ObjectId(max)) {
        max = id;
      }
    }
    return max;
  }

  async markRoomRead(input: {
    userId: string;
    roomId: string;
    messageId: string;
  }): Promise<{
    roomId: string;
    userId: string;
    messageId: string;
    unreadCount: number;
  }> {
    if (!Types.ObjectId.isValid(input.roomId)) {
      throw new BadRequestException('Invalid room id');
    }
    if (!Types.ObjectId.isValid(input.messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(input.userId);
    await this.assertRoomForUser(input.userId, input.roomId);
    const roomObjectId = new Types.ObjectId(input.roomId);
    const messageOid = new Types.ObjectId(input.messageId);
    const messageExists = await this.messageModel
      .findOne({
        _id: messageOid,
        roomId: roomObjectId,
        deletedAt: null,
      })
      .select('_id')
      .lean()
      .exec();
    if (!messageExists) {
      throw new BadRequestException('Message not found in room');
    }
    const existing = await this.chatReadStateModel
      .findOne({ roomId: roomObjectId, userId: input.userId })
      .lean()
      .exec();
    const prev = existing?.lastReadMessageId?.trim();
    const advanced =
      !prev ||
      !Types.ObjectId.isValid(prev) ||
      messageOid > new Types.ObjectId(prev);
    const lastReadMessageId = advanced ? input.messageId : prev;
    if (advanced) {
      await this.chatReadStateModel
        .updateOne(
          { roomId: roomObjectId, userId: input.userId },
          { $set: { lastReadMessageId: input.messageId } },
          { upsert: true },
        )
        .exec();
    }
    const unreadMap = await this.countUnreadByRoomIds(input.userId, [
      input.roomId,
    ]);
    return {
      roomId: input.roomId,
      userId: input.userId,
      messageId: lastReadMessageId,
      unreadCount: unreadMap.get(input.roomId) ?? 0,
    };
  }

  async deleteMessage(
    userId: string,
    messageId: string,
  ): Promise<{
    messageId: string;
    roomId: string;
    pinChanged: ChatRoomPinChangedPayload | null;
  }> {
    const trimmedId = messageId.trim();
    if (!Types.ObjectId.isValid(trimmedId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    const message = await this.messageModel.findById(trimmedId).exec();
    if (!message) {
      throw new NotFoundException('Message not found');
    }
    const roomId = this.asIdString(message.roomId);
    if (!roomId) {
      throw new NotFoundException('Message not found');
    }
    const room = await this.chatRoomsService.findById(roomId);
    if (!room || room.archivedAt) {
      throw new NotFoundException('Message not found');
    }
    await this.assertRoomForUser(userId, roomId);
    const lean = message.toObject<MessageLean>();
    await this.assertMayModerateOthersMessage(actor, {
      ...lean,
      allianceId: room.allianceId,
    });
    const messageOid = new Types.ObjectId(trimmedId);
    const res = await this.messageModel.deleteOne({ _id: messageOid }).exec();
    if (res.deletedCount === 1) {
      await this.messageModel
        .updateMany(
          {
            roomId: message.roomId,
            replyToMessageId: messageOid,
          },
          { $set: { replyToMessageId: null } },
        )
        .exec();
    }
    let pinChanged: ChatRoomPinChangedPayload | null = null;
    if (res.deletedCount === 1) {
      const cleared = await this.chatRoomsService.clearPinnedMessageIfMatches(
        roomId,
        trimmedId,
      );
      if (cleared) {
        const updatedRoom = await this.chatRoomsService.findById(roomId);
        if (updatedRoom) {
          pinChanged = await this.roomPinPayloadFromDoc(roomId, updatedRoom);
        }
      }
    }
    return { messageId: trimmedId, roomId, pinChanged };
  }

  /**
   * R5 admin: wipe all chat messages, read cursors, and attachment metadata.
   * Chat rooms are kept (same as scripts/clear-chat-messages.mjs).
   */
  async clearAllChatHistoryForAdmin(): Promise<ChatClearHistoryForAdminResult> {
    const [messages, readStates, attachmentsDeleted, pinsCleared] =
      await Promise.all([
        this.messageModel.deleteMany({}).exec(),
        this.chatReadStateModel.deleteMany({}).exec(),
        this.chatAttachments.deleteAllMetadataForAdmin(),
        this.chatRoomsService.clearAllPinsEverywhere(),
      ]);
    return {
      messagesDeleted: messages.deletedCount ?? 0,
      readStatesDeleted: readStates.deletedCount ?? 0,
      attachmentsDeleted,
      pinsCleared,
    };
  }
}
