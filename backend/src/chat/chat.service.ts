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
import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/global-chat-alliance-id';
import { resolveChatAllianceScope } from './chat-alliance-scope';
import { userMayAccessChatRoom } from './chat-room-access';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { PlayerTeamMemberRole } from '../common/enums/player-team-member-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UserDocument } from '../users/schemas/user.schema';
import { TeamsService } from '../users/teams.service';
import { UsersService } from '../users/users.service';
import { StickerAccessService } from '../users/sticker-access.service';
import { ChatRoomsService } from './chat-rooms.service';
import { Message, MessageAttachment } from './schemas/message.schema';
import { ChatRoomReadState } from './schemas/chat-room-read-state.schema';
import {
  parseZlobyakaStickerStem,
  ZLOBYAKA_STICKER_STEMS,
} from './zlobyaka-stickers.const';

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
  text: string;
  editedAt?: Date | null;
  forwardedFrom?:
    | {
        messageId: Types.ObjectId;
        senderId: string;
        senderUsername: string;
        senderRole: PlayerTeamMemberRole;
        senderTeamTag?: string | null;
      }
    | null;
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
  /** Telegram @handle without @, from sender profile at read time (or at send). */
  senderTelegramUsername: string | null;
  text: string;
  editedAt: string | null;
  forwardedFrom:
    | {
        messageId: string;
        senderId: string;
        senderUsername: string;
        senderRole: PlayerTeamMemberRole;
        senderTeamTag: string | null;
      }
    | null;
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

@Injectable()
export class ChatService {
  constructor(
    @InjectModel(Message.name) private readonly messageModel: Model<Message>,
    @InjectModel(ChatRoomReadState.name)
    private readonly chatReadStateModel: Model<ChatRoomReadState>,
    private readonly usersService: UsersService,
    @Inject(forwardRef(() => TeamsService))
    private readonly teamsService: TeamsService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly stickerAccess: StickerAccessService,
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

  private serializeReplyPreview(
    message: MessageLean,
    senderTeamTagMap?: Map<string, string | null>,
  ): ChatMessageReplyPreview {
    const senderTeamTag =
      message.senderTeamTag ?? senderTeamTagMap?.get(message.senderId) ?? null;
    return {
      _id: this.asIdString(message._id)!,
      senderId: message.senderId,
      senderUsername: message.senderUsername,
      senderRole: message.senderRole,
      senderTeamTag,
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
      senderTelegramUsername: senderTelegramMap?.get(message.senderId) ?? null,
      text: message.deletedAt ? '' : message.text,
      editedAt: this.toIso(message.editedAt),
      forwardedFrom: message.deletedAt ? null : forwardedFrom,
      reactions: message.deletedAt ? [] : reactions,
      attachments,
      createdAt: this.toIso(message.createdAt),
      updatedAt: this.toIso(message.updatedAt),
      replyToMessageId,
      replyTo: replyTarget
        ? this.serializeReplyPreview(replyTarget, senderTeamTagMap)
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
      ),
    );
  }

  /**
   * If the message is exactly a Zlobyaka sticker wire payload, the stem must be in the catalog.
   */
  private assertZlobyakaStickerPayload(text: string): void {
    const stem = parseZlobyakaStickerStem(text);
    if (!stem) return;
    if (!ZLOBYAKA_STICKER_STEMS.has(stem)) {
      throw new BadRequestException('Unknown sticker');
    }
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
    const now = new Date();
    if (authorUser.mutedUntil && authorUser.mutedUntil > now) {
      throw new ForbiddenException(
        'You are temporarily muted in alliance chat',
      );
    }

    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      input.author.userId,
      input.roomId,
    );
    if (
      allianceId === GLOBAL_CHAT_ALLIANCE_ID &&
      !this.hasCompleteTeamBranding(authorUser)
    ) {
      throw new ForbiddenException('GLOBAL_CHAT_TEAM_PROFILE_REQUIRED');
    }
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
    this.assertZlobyakaStickerPayload(trimmedText);
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
      senderUsername: input.author.username,
      senderRole: senderSquadRole,
      senderTeamTag: authorUser.teamTag ?? null,
      replyToMessageId: replyTarget?._id ?? null,
      deletedAt: null,
      deletedByUserId: null,
    });
    return this.viewMessageForUser(
      created.toObject<MessageLean>(),
      input.author.userId,
    );
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
    const before = options?.before?.trim();
    if (before) {
      if (!Types.ObjectId.isValid(before)) {
        throw new BadRequestException('Invalid before cursor');
      }
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    const messages = await this.messageModel
      .find(filter)
      .sort({ createdAt: -1 })
      .limit(limit)
      .lean<MessageLean[]>()
      .exec();
    return this.enrichMessages(messages, userId);
  }

  async editMessage(
    userId: string,
    messageId: string,
    text: string,
  ): Promise<ChatMessageView> {
    if (!Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) throw new ForbiddenException('User not found');
    const message = await this.messageModel.findById(messageId).exec();
    if (!message) throw new NotFoundException('Message not found');
    const mayEdit =
      message.senderId === userId || actor.role === AllianceRole.R5;
    if (!mayEdit) {
      throw new ForbiddenException('You may only edit your own messages');
    }
    const trimmed = text.trim();
    if (!trimmed) throw new BadRequestException('Text is required');
    this.assertZlobyakaStickerPayload(trimmed);
    await this.stickerAccess.assertUserMaySendStickerMessage(actor, trimmed);
    message.text = trimmed;
    message.editedAt = new Date();
    await message.save();
    return this.viewMessageForUser(message.toObject<MessageLean>(), userId);
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
    const trimmed = emoji.trim();
    if (!trimmed) throw new BadRequestException('emoji is required');
    const message = await this.messageModel.findById(messageId).exec();
    if (!message) throw new NotFoundException('Message not found');
    const list = (message.reactions ?? []) as { emoji: string; userIds: string[] }[];
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
    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      userId,
      roomId,
    );
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
    this.assertZlobyakaStickerPayload(fwdText);
    await this.stickerAccess.assertUserMaySendStickerMessage(actor, fwdText);
    const squadRoleMap = await this.teamsService.resolveSquadRolesByUserIds([
      userId,
      source.senderId,
    ]);
    const actorSquadRole =
      squadRoleMap.get(userId) ?? PlayerTeamMemberRole.R1;
    const sourceSquadRole =
      squadRoleMap.get(source.senderId) ?? PlayerTeamMemberRole.R1;
    const created = await this.messageModel.create({
      allianceId,
      roomId: roomObjectId,
      text: fwdText,
      attachments: source.attachments ?? [],
      senderId: userId,
      senderUsername: actor.username,
      senderRole: actorSquadRole,
      senderTeamTag: actor.teamTag ?? null,
      replyToMessageId: null,
      deletedAt: null,
      deletedByUserId: null,
      forwardedFrom: {
        messageId: new Types.ObjectId(sourceMessageId),
        senderId: source.senderId,
        senderUsername: source.senderUsername,
        senderRole: sourceSquadRole,
        senderTeamTag: source.senderTeamTag ?? null,
      },
      editedAt: null,
      reactions: [],
    });
    return this.viewMessageForUser(created.toObject<MessageLean>(), userId);
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

    const readStates = await this.chatReadStateModel
      .find({ userId, roomId: { $in: valid } })
      .lean()
      .exec();
    const readByRoom = new Map(
      readStates.map((r) => [
        (r.roomId as Types.ObjectId).toString(),
        r.lastReadMessageId,
      ]),
    );

    await Promise.all(
      valid.map(async (roomOid) => {
        const key = roomOid.toString();
        const lastRead = readByRoom.get(key);
        const filter: Record<string, unknown> = {
          roomId: roomOid,
          deletedAt: null,
          senderId: { $ne: userId },
        };
        if (lastRead && Types.ObjectId.isValid(lastRead)) {
          filter._id = { $gt: new Types.ObjectId(lastRead) };
        }
        const n = await this.messageModel.countDocuments(filter).exec();
        out.set(key, n);
      }),
    );
    return out;
  }

  async markRoomRead(input: {
    userId: string;
    roomId: string;
    messageId: string;
  }): Promise<{ roomId: string; userId: string; messageId: string }> {
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
    const existing = await this.chatReadStateModel
      .findOne({ roomId: roomObjectId, userId: input.userId })
      .lean()
      .exec();
    const prev = existing?.lastReadMessageId?.trim();
    const advanced =
      !prev ||
      !Types.ObjectId.isValid(prev) ||
      messageOid > new Types.ObjectId(prev);
    const lastReadMessageId = advanced ? input.messageId : prev!;
    if (advanced) {
      await this.chatReadStateModel
        .updateOne(
          { roomId: roomObjectId, userId: input.userId },
          { $set: { lastReadMessageId: input.messageId } },
          { upsert: true },
        )
        .exec();
    }
    return {
      roomId: input.roomId,
      userId: input.userId,
      messageId: lastReadMessageId,
    };
  }

  async deleteMessage(
    userId: string,
    messageId: string,
  ): Promise<{ messageId: string; roomId: string }> {
    if (!Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    const message = await this.messageModel.findById(messageId).exec();
    if (!message) {
      throw new NotFoundException('Message not found');
    }
    if (
      message.allianceId !== GLOBAL_CHAT_ALLIANCE_ID &&
      message.allianceId !== resolveChatAllianceScope(actor)
    ) {
      throw new ForbiddenException(
        'Message is not available for your alliance',
      );
    }
    const mayDelete =
      message.senderId === userId || actor.role === AllianceRole.R5;
    if (!mayDelete) {
      throw new ForbiddenException('You may only delete your own messages');
    }
    const roomId = this.asIdString(message.roomId)!;
    const res = await this.messageModel
      .deleteOne({
        _id: new Types.ObjectId(messageId),
        allianceId: message.allianceId,
      })
      .exec();
    if (res.deletedCount !== 1) {
      throw new NotFoundException('Message not found');
    }
    await this.messageModel
      .updateMany(
        {
          allianceId: message.allianceId,
          roomId: message.roomId,
          replyToMessageId: new Types.ObjectId(messageId),
        },
        { $set: { replyToMessageId: null } },
      )
      .exec();
    return { messageId, roomId };
  }
}
