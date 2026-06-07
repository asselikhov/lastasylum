import {
  BadRequestException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
  forwardRef,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import mongoose, { Model, Types } from 'mongoose';
import {
  isSquadOfficerRole,
  PlayerTeamMemberRole,
} from '../common/enums/player-team-member-role.enum';
import { assertStickerPayload } from '../chat/sticker-payload.util';
import { User, UserDocument } from './schemas/user.schema';
import {
  TeamForumMessage,
  TeamForumMessageDocument,
} from './schemas/team-forum-message.schema';
import {
  TeamForumTopic,
  TeamForumTopicDocument,
} from './schemas/team-forum-topic.schema';
import { TeamForumTopicReadState } from './schemas/team-forum-topic-read-state.schema';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { GameIdentitiesService } from './game-identities.service';
import { buildAvatarRelativeUrl } from './user-avatar.util';
import { TeamsService } from './teams.service';
import { StickerAccessService } from './sticker-access.service';
import { UsersService } from './users.service';
import {
  buildPinnedPreviewFromForumMessage,
  buildStubPinnedPreview,
  enrichPinnedPreview,
  PinnedMessagePreview,
} from '../common/pinned-message-preview';
import {
  activePinFromHistory,
  clearAllPinHistory,
  ensurePinHistoryMigrated,
  normalizePinHistory,
  pushPinHistoryEntry,
  removePinHistoryEntry,
  type PinHistoryEntry,
} from '../common/pin-history.util';
import { PinAuditService } from './pin-audit.service';

export type TeamForumTopicRow = {
  id: string;
  teamId: string;
  title: string;
  createdByUserId: string;
  /** Profile avatar for topic creator. */
  createdByAvatarRelativeUrl: string | null;
  messageCount: number;
  unreadCount: number;
  lastReadMessageId: string | null;
  lastMessageAt: string | null;
  /** Author of the newest non-deleted message in the topic (for list avatars). */
  lastMessageSenderUserId: string | null;
  lastMessageSenderUsername: string | null;
  lastMessageSenderAvatarRelativeUrl: string | null;
  createdAt: string;
  updatedAt: string;
  pinnedMessageId: string | null;
  pinnedAt: string | null;
  pinnedByUserId: string | null;
  pinnedMessage: PinnedMessagePreview | null;
  pinnedMessages: PinnedMessagePreview[];
};

export type TeamForumTopicPinChangedPayload = {
  teamId: string;
  topicId: string;
  pinnedMessageId: string | null;
  pinnedAt: string | null;
  pinnedByUserId: string | null;
  pinnedMessage: PinnedMessagePreview | null;
  pinnedMessages: PinnedMessagePreview[];
};

export type TeamForumMessageRow = {
  id: string;
  topicId: string;
  teamId: string;
  senderUserId: string;
  senderUsername: string;
  /** Profile avatar for sender in forum thread UI. */
  senderAvatarRelativeUrl: string | null;
  senderRole: PlayerTeamMemberRole;
  senderTeamTag: string | null;
  senderServerNumber: number | null;
  text: string;
  replyToMessageId: string | null;
  replyTo: {
    id: string;
    senderUsername: string;
    senderRole: PlayerTeamMemberRole;
    senderTeamTag: string | null;
    senderServerNumber: number | null;
    text: string;
  } | null;
  editedAt: string | null;
  deletedAt: string | null;
  deletedByUserId: string | null;
  imageRelativeUrl: string | null;
  imageRelativeUrls: string[];
  fileRelativeUrl: string | null;
  fileFilename: string | null;
  forwardedFrom: {
    messageId: string;
    senderUserId: string;
    senderUsername: string;
    senderRole: PlayerTeamMemberRole;
    senderTeamTag: string | null;
    senderServerNumber: number | null;
  } | null;
  reactions: { emoji: string; count: number; reactedByMe: boolean }[];
  clientMessageId: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TeamForumMessageReactionBroadcastPayload = {
  teamId: string;
  topicId: string;
  messageId: string;
  reactions: { emoji: string; count: number; userIds: string[] }[];
};

@Injectable()
export class TeamForumService {
  private readonly unreadSumCache = new Map<
    string,
    { sum: number; atMs: number }
  >();
  private static readonly UNREAD_SUM_CACHE_TTL_MS = 15_000;

  constructor(
    @InjectModel(TeamForumTopic.name)
    private readonly topicModel: Model<TeamForumTopicDocument>,
    @InjectModel(TeamForumMessage.name)
    private readonly messageModel: Model<TeamForumMessageDocument>,
    @InjectModel(TeamForumTopicReadState.name)
    private readonly topicReadStateModel: Model<TeamForumTopicReadState>,
    @InjectModel(User.name) private readonly userModel: Model<User>,
    private readonly teams: TeamsService,
    private readonly teamNewsAttachments: TeamNewsAttachmentsService,
    private readonly stickerAccess: StickerAccessService,
    private readonly gameIdentities: GameIdentitiesService,
    private readonly usersService: UsersService,
    @Inject(forwardRef(() => PinAuditService))
    private readonly pinAudit: PinAuditService,
  ) {}

  private async enrichMessagesWithAvatars(
    rows: TeamForumMessageRow[],
  ): Promise<TeamForumMessageRow[]> {
    if (rows.length === 0) return rows;
    const senderIds = [...new Set(rows.map((r) => r.senderUserId))];
    const avatarMap =
      await this.usersService.findAvatarRelativeUrlsByIds(senderIds);
    const displayNameMap =
      await this.gameIdentities.buildSenderDisplayNameMap(senderIds);
    return rows.map((r) => ({
      ...r,
      senderUsername: this.gameIdentities.coalesceDisplayName(
        r.senderUsername,
        displayNameMap.get(r.senderUserId),
      ),
      replyTo: r.replyTo
        ? {
            ...r.replyTo,
            senderUsername: this.gameIdentities.coalesceDisplayName(
              r.replyTo.senderUsername,
              null,
            ),
          }
        : null,
      senderAvatarRelativeUrl: avatarMap.get(r.senderUserId) ?? null,
    }));
  }

  private async enrichTopicsWithAvatars(
    rows: TeamForumTopicRow[],
  ): Promise<TeamForumTopicRow[]> {
    if (rows.length === 0) return rows;
    const userIds = new Set<string>();
    for (const row of rows) {
      userIds.add(row.createdByUserId);
      const lastId = row.lastMessageSenderUserId?.trim();
      if (lastId) userIds.add(lastId);
    }
    const avatarMap = await this.usersService.findAvatarRelativeUrlsByIds([
      ...userIds,
    ]);
    const displayNameMap = await this.gameIdentities.buildSenderDisplayNameMap([
      ...userIds,
    ]);
    return rows.map((r) => ({
      ...r,
      createdByAvatarRelativeUrl: avatarMap.get(r.createdByUserId) ?? null,
      lastMessageSenderAvatarRelativeUrl: r.lastMessageSenderUserId
        ? (avatarMap.get(r.lastMessageSenderUserId) ?? null)
        : null,
      lastMessageSenderUsername: r.lastMessageSenderUserId
        ? this.gameIdentities.coalesceDisplayName(
            r.lastMessageSenderUsername,
            displayNameMap.get(r.lastMessageSenderUserId),
          )
        : r.lastMessageSenderUsername,
    }));
  }

  private async lastMessageSendersByTopicIds(
    teamId: Types.ObjectId,
    topicIds: Types.ObjectId[],
  ): Promise<
    Map<string, { senderUserId: string; senderUsername: string }>
  > {
    if (topicIds.length === 0) return new Map();
    const agg = await this.messageModel.aggregate<{
      _id: Types.ObjectId;
      senderUserId: string;
      senderUsername: string;
    }>([
      {
        $match: {
          teamId,
          topicId: { $in: topicIds },
          deletedAt: null,
        },
      },
      { $sort: { createdAt: -1 } },
      {
        $group: {
          _id: '$topicId',
          senderUserId: { $first: '$senderUserId' },
          senderUsername: { $first: '$senderUsername' },
        },
      },
    ]);
    return new Map(
      agg.map((row) => [
        row._id.toString(),
        {
          senderUserId: row.senderUserId,
          senderUsername: row.senderUsername,
        },
      ]),
    );
  }

  private async assertCanManageTopicsAsync(
    teamId: string,
    userId: string,
  ): Promise<void> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const role = this.teams.getSquadRoleForUser(team, userId);
    if (!isSquadOfficerRole(role)) {
      throw new ForbiddenException(
        'Only squad ranks R4 and R5 can manage forum topics',
      );
    }
  }

  private async assertMayPinInTeamForum(
    teamId: string,
    userId: string,
  ): Promise<void> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const role = this.teams.getSquadRoleForUser(team, userId);
    if (!isSquadOfficerRole(role)) {
      throw new ForbiddenException(
        'Only squad ranks R4 and R5 can pin forum messages',
      );
    }
  }

  private topicPinChangedPayload(
    teamId: string,
    topicId: string,
    doc: Pick<
      TeamForumTopic,
      'pinnedMessageId' | 'pinnedAt' | 'pinnedByUserId'
    >,
    pinnedMessage: PinnedMessagePreview | null,
    pinnedMessages: PinnedMessagePreview[],
  ): TeamForumTopicPinChangedPayload {
    return {
      teamId,
      topicId,
      pinnedMessageId: doc.pinnedMessageId?.toString() ?? null,
      pinnedAt: doc.pinnedAt?.toISOString() ?? null,
      pinnedByUserId: doc.pinnedByUserId ?? null,
      pinnedMessage,
      pinnedMessages,
    };
  }

  private pinHistoryForTopic(doc: TeamForumTopicDocument | TeamForumTopic): PinHistoryEntry[] {
    return normalizePinHistory(ensurePinHistoryMigrated(doc));
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
      msgs.map((m) => [String(m._id), buildPinnedPreviewFromForumMessage(m)]),
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

  private async topicPinPayloadFromDoc(
    teamId: string,
    topicId: string,
    doc: TeamForumTopicDocument,
  ): Promise<TeamForumTopicPinChangedPayload> {
    const history = this.pinHistoryForTopic(doc);
    const pinnedMessages = await this.buildPinnedMessagesFromHistory(history);
    const activeId = doc.pinnedMessageId?.toString() ?? null;
    const pinnedMessage =
      pinnedMessages.find((p) => p.id === activeId) ?? pinnedMessages[0] ?? null;
    return this.topicPinChangedPayload(
      teamId,
      topicId,
      doc,
      pinnedMessage,
      pinnedMessages,
    );
  }

  private applyPinStateToTopic(
    doc: TeamForumTopicDocument,
    history: PinHistoryEntry[],
  ): void {
    const active = activePinFromHistory(history);
    doc.pinHistory = history;
    doc.pinnedMessageId = active.pinnedMessageId ?? null;
    doc.pinnedAt = active.pinnedAt ?? null;
    doc.pinnedByUserId = active.pinnedByUserId ?? null;
  }

  private async buildPinnedPreviewsForTopics(
    docs: Array<{
      _id: Types.ObjectId;
      pinnedMessageId?: Types.ObjectId | null;
      pinnedByUserId?: string | null;
    }>,
  ): Promise<Map<string, PinnedMessagePreview | null>> {
    const pinIds = [
      ...new Set(
        docs
          .map((d) => d.pinnedMessageId?.toString())
          .filter((id): id is string => !!id && Types.ObjectId.isValid(id)),
      ),
    ];
    const out = new Map<string, PinnedMessagePreview | null>();
    if (pinIds.length === 0) {
      for (const doc of docs) {
        out.set(doc._id.toString(), null);
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
        buildPinnedPreviewFromForumMessage(m),
      ]),
    );
    const pinUserIds = [
      ...new Set(
        docs
          .map((d) => d.pinnedByUserId?.trim())
          .filter((id): id is string => !!id),
      ),
    ];
    const pinUsernames = await this.resolvePinnedByUsernames(pinUserIds);
    for (const doc of docs) {
      const tid = doc._id.toString();
      const pinId = doc.pinnedMessageId?.toString();
      let preview = pinId ? (byMsgId.get(pinId) ?? null) : null;
      if (preview && doc.pinnedByUserId) {
        preview = enrichPinnedPreview(
          preview,
          pinUsernames.get(doc.pinnedByUserId.trim()) ?? null,
        );
      }
      out.set(tid, preview);
    }
    return out;
  }

  private async resolvePinnedByUsernames(
    userIds: string[],
  ): Promise<Map<string, string>> {
    return this.gameIdentities.buildSenderDisplayNameMap(userIds);
  }

  private lastSenderFromTopicDoc(doc: TeamForumTopicDocument): {
    senderUserId: string | null;
    senderUsername: string | null;
  } {
    const senderUserId = doc.lastMessageSenderUserId?.trim() ?? null;
    if (!senderUserId) {
      return { senderUserId: null, senderUsername: null };
    }
    return {
      senderUserId,
      senderUsername: doc.lastMessageSenderUsername?.trim() ?? null,
    };
  }

  private invalidateUnreadSumCache(teamId: string, userId: string): void {
    this.unreadSumCache.delete(`${teamId}:${userId}`);
  }

  private async invalidateUnreadSumCacheForTeam(teamId: string): Promise<void> {
    const memberIds = await this.teams.listSquadMemberUserIds(teamId);
    for (const memberId of memberIds) {
      this.invalidateUnreadSumCache(teamId, memberId);
    }
  }

  private topicRow(
    doc: TeamForumTopicDocument,
    extras?: {
      messageCount?: number;
      unreadCount?: number;
      lastReadMessageId?: string | null;
      lastMessageSenderUserId?: string | null;
      lastMessageSenderUsername?: string | null;
      pinnedMessage?: PinnedMessagePreview | null;
      pinnedMessages?: PinnedMessagePreview[];
    },
  ): TeamForumTopicRow {
    const pinnedMessages = extras?.pinnedMessages ??
      (extras?.pinnedMessage != null ? [extras.pinnedMessage] : []);
    return {
      id: doc._id.toString(),
      teamId: doc.teamId.toString(),
      title: doc.title,
      createdByUserId: doc.createdByUserId,
      createdByAvatarRelativeUrl: null,
      messageCount: extras?.messageCount ?? doc.messageCount ?? 0,
      unreadCount: extras?.unreadCount ?? 0,
      lastReadMessageId: extras?.lastReadMessageId ?? null,
      lastMessageAt: doc.lastMessageAt ? doc.lastMessageAt.toISOString() : null,
      lastMessageSenderUserId:
        extras?.lastMessageSenderUserId ??
        doc.lastMessageSenderUserId?.trim() ??
        null,
      lastMessageSenderUsername:
        extras?.lastMessageSenderUsername ??
        doc.lastMessageSenderUsername?.trim() ??
        null,
      lastMessageSenderAvatarRelativeUrl: null,
      pinnedMessageId: doc.pinnedMessageId?.toString() ?? null,
      pinnedAt: doc.pinnedAt?.toISOString() ?? null,
      pinnedByUserId: doc.pinnedByUserId ?? null,
      pinnedMessage: extras?.pinnedMessage ?? null,
      pinnedMessages,
      createdAt: doc.createdAt?.toISOString() ?? new Date().toISOString(),
      updatedAt: doc.updatedAt?.toISOString() ?? new Date().toISOString(),
    };
  }

  private async readStatesByTopicIds(
    userId: string,
    topicIds: Types.ObjectId[],
  ): Promise<Map<string, string>> {
    if (topicIds.length === 0) return new Map();
    const readStates = await this.topicReadStateModel
      .find({ userId, topicId: { $in: topicIds } })
      .lean()
      .exec();
    return new Map(
      readStates.map((r) => [r.topicId.toString(), r.lastReadMessageId]),
    );
  }

  async getLastReadMessageIdsByTopicIds(
    userId: string,
    topicIds: Types.ObjectId[],
  ): Promise<Map<string, string>> {
    return this.readStatesByTopicIds(userId, topicIds);
  }

  private async countUnreadForumMessagesHeavy(
    teamId: Types.ObjectId,
    userId: string,
    topicIds: Types.ObjectId[],
  ): Promise<Map<string, number>> {
    const out = new Map<string, number>();
    if (topicIds.length === 0) return out;

    const readStateCollection = this.topicReadStateModel.collection.name;
    const rows = await this.messageModel.aggregate<{
      _id: Types.ObjectId;
      count: number;
    }>([
      {
        $match: {
          teamId,
          topicId: { $in: topicIds },
          deletedAt: null,
          senderUserId: { $ne: userId },
        },
      },
      {
        $lookup: {
          from: readStateCollection,
          let: { topicOid: '$topicId' },
          pipeline: [
            {
              $match: {
                $expr: {
                  $and: [
                    { $eq: ['$topicId', '$$topicOid'] },
                    { $eq: ['$userId', userId] },
                  ],
                },
              },
            },
            { $project: { lastReadMessageId: 1, _id: 0 } },
            { $limit: 1 },
          ],
          as: 'readState',
        },
      },
      {
        $addFields: {
          lastReadOid: {
            $convert: {
              input: { $arrayElemAt: ['$readState.lastReadMessageId', 0] },
              to: 'objectId',
              onError: null,
              onNull: null,
            },
          },
        },
      },
      {
        $match: {
          $expr: {
            $or: [
              { $eq: ['$lastReadOid', null] },
              { $gt: ['$_id', '$lastReadOid'] },
            ],
          },
        },
      },
      {
        $group: {
          _id: '$topicId',
          count: { $sum: 1 },
        },
      },
    ]);

    for (const row of rows) {
      out.set(row._id.toString(), row.count);
    }
    return out;
  }

  private async countUnreadForumMessages(
    teamId: Types.ObjectId,
    userId: string,
    topicIds: Types.ObjectId[],
    topicDocs?: TeamForumTopicDocument[],
    lastReadMap?: Map<string, string>,
  ): Promise<Map<string, number>> {
    const out = new Map<string, number>();
    if (topicIds.length === 0) return out;

    const docById = new Map(
      (topicDocs ?? []).map((doc) => [doc._id.toString(), doc]),
    );
    const readMap =
      lastReadMap ?? (await this.readStatesByTopicIds(userId, topicIds));
    const needsHeavy: Types.ObjectId[] = [];

    for (const topicId of topicIds) {
      const id = topicId.toString();
      const doc = docById.get(id);
      const lastMsgId = doc?.lastMessageId ?? null;
      if (!lastMsgId) {
        out.set(id, 0);
        continue;
      }
      const lastRead = readMap.get(id);
      if (
        lastRead &&
        Types.ObjectId.isValid(lastRead) &&
        new Types.ObjectId(lastRead) >= lastMsgId
      ) {
        out.set(id, 0);
        continue;
      }
      needsHeavy.push(topicId);
    }

    if (needsHeavy.length > 0) {
      const heavy = await this.countUnreadForumMessagesHeavy(
        teamId,
        userId,
        needsHeavy,
      );
      for (const [topicId, count] of heavy) {
        out.set(topicId, count);
      }
    }
    return out;
  }

  async markTopicRead(
    teamId: string,
    topicId: string,
    userId: string,
    messageId: string,
  ): Promise<{ topicId: string; messageId: string }> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (
      !Types.ObjectId.isValid(topicId) ||
      !Types.ObjectId.isValid(messageId)
    ) {
      throw new BadRequestException('Invalid id');
    }
    const topOid = new Types.ObjectId(topicId);
    const teamOid = new Types.ObjectId(teamId);
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }
    const messageOid = new Types.ObjectId(messageId);
    const existing = await this.topicReadStateModel
      .findOne({ topicId: topOid, userId })
      .lean()
      .exec();
    const prev = existing?.lastReadMessageId?.trim();
    const advanced =
      !prev ||
      !Types.ObjectId.isValid(prev) ||
      messageOid > new Types.ObjectId(prev);
    const lastReadMessageId = advanced ? messageId : prev;
    if (advanced) {
      await this.topicReadStateModel
        .updateOne(
          { topicId: topOid, userId },
          { $set: { lastReadMessageId: messageId } },
          { upsert: true },
        )
        .exec();
      this.invalidateUnreadSumCache(teamId, userId);
    }
    return { topicId, messageId: lastReadMessageId };
  }

  async getPeerReadUptoMessageId(
    teamId: string,
    topicId: string,
    userId: string,
  ): Promise<string | null> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (
      !Types.ObjectId.isValid(teamId) ||
      !Types.ObjectId.isValid(topicId)
    ) {
      throw new BadRequestException('Invalid id');
    }
    const memberIds = await this.teams.listSquadMemberUserIds(teamId);
    const peerIds = memberIds.filter((id) => id !== userId);
    if (peerIds.length === 0) return null;
    const topOid = new Types.ObjectId(topicId);
    const rows = await this.topicReadStateModel
      .find({ topicId: topOid, userId: { $in: peerIds } })
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

  private rethrowMongooseValidation(err: unknown): never {
    if (err instanceof mongoose.Error.ValidationError) {
      const msg = Object.values(err.errors)
        .map((e) => e.message)
        .join('; ');
      throw new BadRequestException(msg || 'Invalid forum message data');
    }
    throw err;
  }

  private asIdString(v: unknown): string | null {
    if (v == null) return null;
    if (typeof v === 'string') return v;
    if (v instanceof Types.ObjectId) return v.toString();
    if (typeof (v as { toString?: unknown }).toString === 'function') {
      return (v as { toString(): string }).toString();
    }
    return null;
  }

  private async applySenderServerNumbersToForumDocs(
    docs: TeamForumMessageDocument[],
  ): Promise<void> {
    const senderIds = [
      ...new Set(
        docs.flatMap((d) => {
          const ids = [d.senderUserId];
          const fwd = d.forwardedFrom?.senderUserId;
          if (fwd) ids.push(fwd);
          return ids;
        }),
      ),
    ];
    const map = await this.gameIdentities.buildSenderServerNumberMap(senderIds);
    for (const d of docs) {
      if (d.senderServerNumber == null || d.senderServerNumber < 1) {
        const n = map.get(d.senderUserId);
        if (n != null) {
          (d as { senderServerNumber: number }).senderServerNumber = n;
        }
      }
      const fwd = d.forwardedFrom;
      if (
        fwd &&
        (fwd.senderServerNumber == null || fwd.senderServerNumber < 1)
      ) {
        const n = map.get(fwd.senderUserId);
        if (n != null) {
          (fwd as { senderServerNumber: number }).senderServerNumber = n;
        }
      }
    }
  }

  private replyPreview(doc: TeamForumMessageDocument): {
    id: string;
    senderUsername: string;
    senderRole: PlayerTeamMemberRole;
    senderTeamTag: string | null;
    senderServerNumber: number | null;
    text: string;
  } {
    return {
      id: doc._id.toString(),
      senderUsername: doc.senderUsername,
      senderRole: doc.senderRole ?? PlayerTeamMemberRole.R1,
      senderTeamTag: doc.senderTeamTag ?? null,
      senderServerNumber: doc.senderServerNumber ?? null,
      text: doc.text,
    };
  }

  private mapReactionsForViewer(
    doc: TeamForumMessageDocument,
    viewerUserId?: string | null,
  ): { emoji: string; count: number; reactedByMe: boolean }[] {
    if (doc.deletedAt) return [];
    return (doc.reactions ?? [])
      .filter((r) => r.emoji && (r.userIds?.length ?? 0) > 0)
      .map((r) => ({
        emoji: r.emoji,
        count: r.userIds.length,
        reactedByMe: viewerUserId ? r.userIds.includes(viewerUserId) : false,
      }));
  }

  messageRow(
    doc: TeamForumMessageDocument,
    replyTarget?: TeamForumMessageDocument | null,
    viewerUserId?: string | null,
  ): TeamForumMessageRow {
    const teamIdStr = doc.teamId.toString();
    const legacyHasImage =
      !doc.deletedAt &&
      doc.imageFileId != null &&
      Types.ObjectId.isValid(doc.imageFileId.toString());
    const albumIds = Array.isArray(doc.imageFileIds) ? doc.imageFileIds : [];
    const albumUrls = !doc.deletedAt
      ? albumIds
          .map((id) => id?.toString?.() ?? '')
          .filter((id) => Types.ObjectId.isValid(id))
          .map((id) => `/teams/${teamIdStr}/news/attachments/${id}`)
      : [];
    const replyId = this.asIdString(doc.replyToMessageId);
    const reply =
      replyId && replyTarget && !replyTarget.deletedAt
        ? this.replyPreview(replyTarget)
        : null;
    return {
      id: doc._id.toString(),
      topicId: doc.topicId.toString(),
      teamId: teamIdStr,
      senderUserId: doc.senderUserId,
      senderUsername: doc.senderUsername,
      senderAvatarRelativeUrl: null,
      senderRole: doc.senderRole ?? PlayerTeamMemberRole.R1,
      senderTeamTag: doc.senderTeamTag ?? null,
      senderServerNumber: doc.senderServerNumber ?? null,
      text: doc.deletedAt ? '' : doc.text,
      replyToMessageId: replyId,
      replyTo: reply,
      editedAt: doc.editedAt ? doc.editedAt.toISOString() : null,
      deletedAt: doc.deletedAt ? doc.deletedAt.toISOString() : null,
      deletedByUserId: doc.deletedByUserId,
      imageRelativeUrl: legacyHasImage
        ? `/teams/${teamIdStr}/news/attachments/${doc.imageFileId!.toString()}`
        : null,
      imageRelativeUrls:
        albumUrls.length > 0
          ? albumUrls
          : legacyHasImage
            ? [
                `/teams/${teamIdStr}/news/attachments/${doc.imageFileId!.toString()}`,
              ]
            : [],
      fileRelativeUrl:
        !doc.deletedAt &&
        doc.fileFileId != null &&
        Types.ObjectId.isValid(doc.fileFileId.toString())
          ? `/teams/${teamIdStr}/news/attachments/${doc.fileFileId.toString()}`
          : null,
      fileFilename: doc.deletedAt ? null : (doc.fileFilename ?? null),
      forwardedFrom: doc.forwardedFrom
        ? {
            messageId: doc.forwardedFrom.messageId.toString(),
            senderUserId: doc.forwardedFrom.senderUserId,
            senderUsername: doc.forwardedFrom.senderUsername,
            senderRole: doc.forwardedFrom.senderRole ?? PlayerTeamMemberRole.R1,
            senderTeamTag: doc.forwardedFrom.senderTeamTag ?? null,
            senderServerNumber: doc.forwardedFrom.senderServerNumber ?? null,
          }
        : null,
      reactions: this.mapReactionsForViewer(doc, viewerUserId),
      clientMessageId: doc.clientMessageId ?? null,
      createdAt: doc.createdAt?.toISOString() ?? new Date().toISOString(),
      updatedAt: doc.updatedAt?.toISOString() ?? new Date().toISOString(),
    };
  }

  async toggleMessageReaction(
    teamId: string,
    topicId: string,
    userId: string,
    messageId: string,
    emoji: string,
  ): Promise<TeamForumMessageRow> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    const trimmed = emoji.trim();
    if (!trimmed) {
      throw new BadRequestException('emoji is required');
    }
    const teamOid = new Types.ObjectId(teamId);
    const topicOid = new Types.ObjectId(topicId);
    const msg = await this.messageModel
      .findOne({
        _id: new Types.ObjectId(messageId),
        teamId: teamOid,
        topicId: topicOid,
        deletedAt: null,
      })
      .exec();
    if (!msg) {
      throw new NotFoundException('Message not found');
    }
    const list = (msg.reactions ?? []) as {
      emoji: string;
      userIds: string[];
    }[];
    const existing = list.find((x) => x.emoji === trimmed);
    if (!existing) {
      list.push({ emoji: trimmed, userIds: [userId] });
    } else {
      const idx = existing.userIds.indexOf(userId);
      if (idx >= 0) {
        existing.userIds.splice(idx, 1);
      } else {
        existing.userIds.push(userId);
      }
    }
    msg.reactions = list.filter((x) => x.userIds.length > 0) as typeof msg.reactions;
    await msg.save();
    const replyId = this.asIdString(msg.replyToMessageId);
    let replyTarget: TeamForumMessageDocument | null = null;
    if (replyId) {
      replyTarget = await this.messageModel.findById(replyId).exec();
    }
    const row = this.messageRow(msg, replyTarget, userId);
    return (await this.enrichMessagesWithAvatars([row]))[0] ?? row;
  }

  async getReactionBroadcastPayload(
    teamId: string,
    topicId: string,
    messageId: string,
  ): Promise<TeamForumMessageReactionBroadcastPayload | null> {
    if (!Types.ObjectId.isValid(messageId)) {
      return null;
    }
    const msg = await this.messageModel
      .findOne({
        _id: new Types.ObjectId(messageId),
        teamId: new Types.ObjectId(teamId),
        topicId: new Types.ObjectId(topicId),
      })
      .lean();
    if (!msg || msg.deletedAt) {
      return null;
    }
    const reactions = (msg.reactions ?? []).map((r) => ({
      emoji: r.emoji,
      count: r.userIds.length,
      userIds: r.userIds,
    }));
    return {
      teamId,
      topicId,
      messageId,
      reactions,
    };
  }

  async listTopicsForAdmin(teamId: string): Promise<TeamForumTopicRow[]> {
    await this.teams.assertTeamExistsForAdmin(teamId);
    const tid = new Types.ObjectId(teamId);
    const rows = await this.topicModel
      .find({ teamId: tid })
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .limit(100)
      .lean();
    const topicIds = rows.map((r) => (r as { _id: Types.ObjectId })._id);
    const countAgg = await this.messageModel.aggregate<{
      _id: Types.ObjectId;
      count: number;
    }>([
      { $match: { teamId: tid, deletedAt: null } },
      { $group: { _id: '$topicId', count: { $sum: 1 } } },
    ]);
    const countMap = new Map(countAgg.map((c) => [c._id.toString(), c.count]));
    const topicDocs = rows.map((r) => r as unknown as TeamForumTopicDocument);
    const needsSenderAgg = topicDocs.filter(
      (doc) => doc.lastMessageAt && !doc.lastMessageSenderUserId?.trim(),
    );
    const lastSenderMap =
      needsSenderAgg.length > 0
        ? await this.lastMessageSendersByTopicIds(
            tid,
            needsSenderAgg.map((doc) => doc._id),
          )
        : new Map<string, { senderUserId: string; senderUsername: string }>();
    return this.enrichTopicsWithAvatars(
      topicDocs.map((doc) => {
        const id = doc._id.toString();
        const actualCount = countMap.get(id) ?? doc.messageCount ?? 0;
        const denorm = this.lastSenderFromTopicDoc(doc);
        const last = lastSenderMap.get(id);
        return this.topicRow(doc, {
          messageCount: actualCount,
          unreadCount: 0,
          lastReadMessageId: null,
          lastMessageSenderUserId:
            denorm.senderUserId ?? last?.senderUserId ?? null,
          lastMessageSenderUsername:
            denorm.senderUsername ?? last?.senderUsername ?? null,
        });
      }),
    );
  }

  async listMessagesForAdmin(
    teamId: string,
    topicId: string,
    limitRaw = 100,
  ): Promise<TeamForumMessageRow[]> {
    await this.teams.assertTeamExistsForAdmin(teamId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }
    const limit = Math.min(200, Math.max(1, Math.floor(limitRaw)));
    const docs = await this.messageModel
      .find({ teamId: teamOid, topicId: topOid, deletedAt: null })
      .sort({ createdAt: 1 })
      .limit(limit)
      .exec();
    await this.applySenderServerNumbersToForumDocs(docs);
    return this.enrichMessagesWithAvatars(
      docs.map((doc) => this.messageRow(doc, null)),
    );
  }

  /** Sum of per-topic unread message counts for the member (excludes own messages). */
  async sumUnreadMessages(teamId: string, userId: string): Promise<number> {
    const cacheKey = `${teamId}:${userId}`;
    const cached = this.unreadSumCache.get(cacheKey);
    if (
      cached &&
      Date.now() - cached.atMs < TeamForumService.UNREAD_SUM_CACHE_TTL_MS
    ) {
      return cached.sum;
    }
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const tid = new Types.ObjectId(teamId);
    const topicLimit = 100;
    const rows = await this.topicModel
      .find({ teamId: tid })
      .select(
        '_id lastMessageId lastMessageSenderUserId lastMessageSenderUsername',
      )
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .limit(topicLimit)
      .lean();
    const topicDocs = rows as unknown as TeamForumTopicDocument[];
    const topicIds = topicDocs.map((r) => r._id);
    const unreadMap = await this.countUnreadForumMessages(
      tid,
      userId,
      topicIds,
      topicDocs,
    );
    let sum = 0;
    for (const count of unreadMap.values()) {
      sum += count;
    }
    this.unreadSumCache.set(cacheKey, { sum, atMs: Date.now() });
    return sum;
  }

  private async buildPinnedMessagesForTopicsBatch(
    docs: TeamForumTopicDocument[],
  ): Promise<Map<string, PinnedMessagePreview[]>> {
    const histories = docs.map((doc) => ({
      topicId: doc._id.toString(),
      history: this.pinHistoryForTopic(doc),
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
      msgs.map((m) => [String(m._id), buildPinnedPreviewFromForumMessage(m)]),
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
    for (const { topicId, history } of histories) {
      const previews: PinnedMessagePreview[] = [];
      for (const entry of history) {
        const preview = byId.get(entry.messageId.toString());
        if (!preview) continue;
        previews.push(
          enrichPinnedPreview(
            preview,
            actorNames.get(entry.pinnedByUserId.trim()) ?? null,
          ),
        );
      }
      out.set(topicId, previews);
    }
    return out;
  }

  async listTopics(
    teamId: string,
    userId: string,
    options?: { view?: 'list' | 'full'; limit?: number },
  ): Promise<TeamForumTopicRow[]> {
    const startedAt = Date.now();
    const view = options?.view === 'full' ? 'full' : 'list';
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const tid = new Types.ObjectId(teamId);
    const topicLimit = Math.min(Math.max(options?.limit ?? 100, 1), 100);
    const rows = await this.topicModel
      .find({ teamId: tid })
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .limit(topicLimit)
      .lean();
    const topicDocs = rows.map((r) => r as unknown as TeamForumTopicDocument);
    const topicIds = topicDocs.map((doc) => doc._id);
    const needsSenderAgg = topicDocs.filter(
      (doc) => doc.lastMessageAt && !doc.lastMessageSenderUserId?.trim(),
    );
    const senderAggPromise =
      needsSenderAgg.length > 0
        ? this.lastMessageSendersByTopicIds(
            tid,
            needsSenderAgg.map((doc) => doc._id),
          )
        : Promise.resolve(
            new Map<string, { senderUserId: string; senderUsername: string }>(),
          );
    const countAggPromise =
      view === 'full'
        ? this.messageModel.aggregate<{
            _id: Types.ObjectId;
            count: number;
          }>([
            { $match: { teamId: tid, deletedAt: null } },
            { $group: { _id: '$topicId', count: { $sum: 1 } } },
          ])
        : Promise.resolve([]);
    const [
      lastReadMap,
      pinPreviews,
      pinnedMessagesMap,
      lastSenderAggMap,
      countAggRows,
    ] = await Promise.all([
      this.getLastReadMessageIdsByTopicIds(userId, topicIds),
      this.buildPinnedPreviewsForTopics(
        rows as Array<{
          _id: Types.ObjectId;
          pinnedMessageId?: Types.ObjectId | null;
        }>,
      ),
      view === 'full'
        ? this.buildPinnedMessagesForTopicsBatch(topicDocs)
        : Promise.resolve(new Map<string, PinnedMessagePreview[]>()),
      senderAggPromise,
      countAggPromise,
    ]);
    const countMap: Map<string, number> =
      view === 'full'
        ? new Map(
            (countAggRows as Array<{ _id: Types.ObjectId; count: number }>).map(
              (c) => [c._id.toString(), c.count] as const,
            ),
          )
        : new Map<string, number>();
    const unreadMap = await this.countUnreadForumMessages(
      tid,
      userId,
      topicIds,
      topicDocs,
      lastReadMap,
    );
    const result = await this.enrichTopicsWithAvatars(
      topicDocs.map((doc) => {
        const id = doc._id.toString();
        const actualCount =
          view === 'full'
            ? (countMap.get(id) ?? doc.messageCount ?? 0)
            : (doc.messageCount ?? 0);
        const denorm = this.lastSenderFromTopicDoc(doc);
        const last = lastSenderAggMap.get(id);
        const pinnedMessages = pinnedMessagesMap.get(id) ?? [];
        const activePreview =
          pinPreviews.get(id) ??
          pinnedMessages.find((p) => p.id === doc.pinnedMessageId?.toString()) ??
          pinnedMessages[0] ??
          null;
        return this.topicRow(doc, {
          messageCount: actualCount,
          unreadCount: unreadMap.get(id) ?? 0,
          lastReadMessageId: lastReadMap.get(id) ?? null,
          lastMessageSenderUserId:
            denorm.senderUserId ?? last?.senderUserId ?? null,
          lastMessageSenderUsername:
            denorm.senderUsername ?? last?.senderUsername ?? null,
          pinnedMessage: activePreview,
          pinnedMessages: view === 'full' ? pinnedMessages : [],
        });
      }),
    );
    const elapsedMs = Date.now() - startedAt;
    if (elapsedMs > 250) {
      console.log(
        `[PerfDiag] listTopics teamId=${teamId} view=${view} topics=${result.length} ms=${elapsedMs}`,
      );
    }
    return result;
  }

  async setTopicPinnedMessage(
    teamId: string,
    topicId: string,
    userId: string,
    messageId: string | null,
  ): Promise<{
    topic: TeamForumTopicRow;
    pinChanged: TeamForumTopicPinChangedPayload;
  }> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    await this.assertMayPinInTeamForum(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }

    const trimmed = messageId?.trim() ?? '';
    if (!trimmed) {
      this.applyPinStateToTopic(topic, []);
      await topic.save();
      const pinChanged = await this.topicPinPayloadFromDoc(teamId, topicId, topic);
      const row = this.topicRow(topic, {
        pinnedMessage: null,
        pinnedMessages: pinChanged.pinnedMessages,
      });
      await this.pinAudit.append({
        teamId,
        scope: 'forum',
        scopeId: topicId,
        messageId: null,
        action: 'unpin_all',
        userId,
      });
      return { topic: row, pinChanged };
    }

    if (!Types.ObjectId.isValid(trimmed)) {
      throw new BadRequestException('Invalid message id');
    }
    const msg = await this.messageModel.findOne({
      _id: new Types.ObjectId(trimmed),
      topicId: topOid,
      teamId: teamOid,
      deletedAt: null,
    });
    if (!msg) {
      throw new NotFoundException('Message not found');
    }

    const history = pushPinHistoryEntry(this.pinHistoryForTopic(topic), {
      messageId: msg._id,
      pinnedAt: new Date(),
      pinnedByUserId: userId,
    });
    this.applyPinStateToTopic(topic, history);
    await topic.save();
    const pinChanged = await this.topicPinPayloadFromDoc(teamId, topicId, topic);
    const row = this.topicRow(topic, {
      pinnedMessage: pinChanged.pinnedMessage,
      pinnedMessages: pinChanged.pinnedMessages,
    });
    await this.pinAudit.append({
      teamId,
      scope: 'forum',
      scopeId: topicId,
      messageId: trimmed,
      action: 'pin',
      userId,
    });
    return { topic: row, pinChanged };
  }

  async unpinOneTopicMessage(
    teamId: string,
    topicId: string,
    userId: string,
    messageId: string,
  ): Promise<{
    topic: TeamForumTopicRow;
    pinChanged: TeamForumTopicPinChangedPayload;
  }> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    await this.assertMayPinInTeamForum(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const trimmed = messageId?.trim() ?? '';
    if (!trimmed || !Types.ObjectId.isValid(trimmed)) {
      throw new BadRequestException('Invalid message id');
    }
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }
    const history = removePinHistoryEntry(
      this.pinHistoryForTopic(topic),
      trimmed,
    );
    this.applyPinStateToTopic(topic, history);
    await topic.save();
    const pinChanged = await this.topicPinPayloadFromDoc(teamId, topicId, topic);
    const row = this.topicRow(topic, {
      pinnedMessage: pinChanged.pinnedMessage,
      pinnedMessages: pinChanged.pinnedMessages,
    });
    await this.pinAudit.append({
      teamId,
      scope: 'forum',
      scopeId: topicId,
      messageId: trimmed,
      action: 'unpin',
      userId,
    });
    return { topic: row, pinChanged };
  }

  private async clearTopicPinIfMessage(
    teamId: Types.ObjectId,
    topicId: Types.ObjectId,
    messageId: string,
  ): Promise<TeamForumTopicPinChangedPayload | null> {
    if (!Types.ObjectId.isValid(messageId)) {
      return null;
    }
    const topic = await this.topicModel.findOne({
      _id: topicId,
      teamId,
    });
    if (!topic) {
      return null;
    }
    const history = this.pinHistoryForTopic(topic);
    const hadPin = history.some((h) => h.messageId.toString() === messageId);
    if (!hadPin) return null;
    const nextHistory = removePinHistoryEntry(history, messageId);
    this.applyPinStateToTopic(topic, nextHistory);
    await topic.save();
    return this.topicPinPayloadFromDoc(
      teamId.toString(),
      topicId.toString(),
      topic,
    );
  }

  async createTopic(
    teamId: string,
    userId: string,
    title: string,
  ): Promise<TeamForumTopicRow> {
    await this.assertCanManageTopicsAsync(teamId, userId);
    const tid = new Types.ObjectId(teamId);
    const doc = await this.topicModel.create({
      teamId: tid,
      title: title.trim(),
      createdByUserId: userId,
      lastMessageAt: null,
      messageCount: 0,
    });
    return this.topicRow(doc);
  }

  async updateTopic(
    teamId: string,
    topicId: string,
    userId: string,
    title: string,
  ): Promise<TeamForumTopicRow> {
    await this.assertCanManageTopicsAsync(teamId, userId);
    const t = await this.topicModel.findOne({
      _id: new Types.ObjectId(topicId),
      teamId: new Types.ObjectId(teamId),
    });
    if (!t) {
      throw new NotFoundException('Topic not found');
    }
    t.title = title.trim();
    await t.save();
    return this.topicRow(t);
  }

  async deleteTopic(
    teamId: string,
    topicId: string,
    userId: string,
  ): Promise<void> {
    await this.assertCanManageTopicsAsync(teamId, userId);
    const tid = new Types.ObjectId(topicId);
    const teamOid = new Types.ObjectId(teamId);
    const t = await this.topicModel.findOne({ _id: tid, teamId: teamOid });
    if (!t) {
      throw new NotFoundException('Topic not found');
    }
    await this.messageModel.deleteMany({ topicId: tid, teamId: teamOid });
    await t.deleteOne();
  }

  /** Lightweight membership + topic access check (socket topic:join, no message fetch). */
  async assertTopicMemberAccess(
    teamId: string,
    topicId: string,
    userId: string,
  ): Promise<void> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(topicId)) {
      throw new NotFoundException('Topic not found');
    }
    const topic = await this.topicModel
      .findOne({
        _id: new Types.ObjectId(topicId),
        teamId: new Types.ObjectId(teamId),
      })
      .select('_id')
      .lean();
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }
  }

  async listMessages(
    teamId: string,
    topicId: string,
    userId: string,
    before?: string,
    limitRaw?: number,
  ): Promise<TeamForumMessageRow[]> {
    const startedAt = Date.now();
    await this.assertTopicMemberAccess(teamId, topicId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const limit = Math.min(Math.max(limitRaw ?? 50, 1), 100);
    const filter: Record<string, unknown> = {
      topicId: topOid,
      teamId: teamOid,
      deletedAt: null,
    };
    if (before && Types.ObjectId.isValid(before)) {
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    const rows = await this.messageModel
      .find(filter)
      .sort({ _id: -1 })
      .limit(limit)
      .lean();
    const docs = rows as unknown as TeamForumMessageDocument[];

    const replyIds = [
      ...new Set(
        docs
          .map((d) => this.asIdString(d.replyToMessageId))
          .filter((x): x is string => Boolean(x && Types.ObjectId.isValid(x))),
      ),
    ];
    const replyDocs =
      replyIds.length > 0
        ? await this.messageModel
            .find({
              _id: { $in: replyIds.map((id) => new Types.ObjectId(id)) },
              teamId: teamOid,
              topicId: topOid,
            })
            .lean()
        : [];
    const replyDocList = replyDocs as unknown as TeamForumMessageDocument[];

    // Backward compatibility: older forum messages may not have senderRole/senderTeamTag persisted.
    const senderIds = [
      ...new Set(
        [...docs, ...replyDocList]
          .map((d) => d.senderUserId)
          .filter((x) => Boolean(x)),
      ),
    ];
    const senderTeamTagMap = new Map(
      (
        await this.userModel
          .find({
            _id: {
              $in: senderIds
                .filter((id) => Types.ObjectId.isValid(id))
                .map((id) => new Types.ObjectId(id)),
            },
          })
          .select('teamTag')
          .lean<Array<{ _id: Types.ObjectId; teamTag?: string | null }>>()
          .exec()
      ).map((u) => [u._id.toString(), u.teamTag ?? null]),
    );
    const fillSenderSnapshots = (d: TeamForumMessageDocument) => {
      const needRole = !(d as any).senderRole;
      const needTag = (d as any).senderTeamTag == null;
      if (needRole) {
        (d as any).senderRole =
          this.teams.getSquadRoleForUser(team, d.senderUserId) ??
          PlayerTeamMemberRole.R1;
      }
      if (needTag) {
        (d as any).senderTeamTag = senderTeamTagMap.get(d.senderUserId) ?? null;
      }
    };
    for (const d of docs) fillSenderSnapshots(d);
    for (const d of replyDocList) fillSenderSnapshots(d);

    const replyMap = new Map(replyDocList.map((d) => [d._id.toString(), d]));
    const allDocs = [...docs, ...replyDocList];
    const needsServerNumbers = allDocs.some(
      (d) =>
        d.senderServerNumber == null ||
        d.senderServerNumber < 1 ||
        (d.forwardedFrom != null &&
          (d.forwardedFrom.senderServerNumber == null ||
            d.forwardedFrom.senderServerNumber < 1)),
    );
    if (needsServerNumbers) {
      await this.applySenderServerNumbersToForumDocs(allDocs);
    }
    const result = await this.enrichMessagesWithAvatars(
      docs
        .map((d) => {
          const rid = this.asIdString(d.replyToMessageId);
          return this.messageRow(d, rid ? replyMap.get(rid) : null, userId);
        })
        .reverse(),
    );
    const elapsedMs = Date.now() - startedAt;
    if (elapsedMs > 250) {
      console.log(
        `[PerfDiag] listMessages teamId=${teamId} topicId=${topicId} messages=${result.length} ms=${elapsedMs}`,
      );
    }
    return result;
  }

  async postMessage(
    teamId: string,
    topicId: string,
    userId: string,
    text: string,
    replyToMessageId?: string | null,
    imageFileIds?: string[],
    imageFileId?: string | null,
    fileFileId?: string | null,
    clientMessageId?: string | null,
  ): Promise<TeamForumMessageRow> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }
    const normalizedClientMessageId =
      typeof clientMessageId === 'string' && clientMessageId.trim()
        ? clientMessageId.trim().slice(0, 64)
        : null;
    if (normalizedClientMessageId) {
      const existing = await this.messageModel
        .findOne({
          senderUserId: userId,
          clientMessageId: normalizedClientMessageId,
          deletedAt: null,
        })
        .exec();
      if (existing) {
        let replyTarget: TeamForumMessageDocument | null = null;
        if (existing.replyToMessageId) {
          replyTarget = await this.messageModel
            .findById(existing.replyToMessageId)
            .exec();
        }
        const senderDoc = await this.userModel.findById(userId).exec();
        const row = this.messageRow(existing, replyTarget, userId);
        return {
          ...row,
          senderAvatarRelativeUrl: senderDoc
            ? buildAvatarRelativeUrl(
                senderDoc._id.toString(),
                senderDoc.avatarKey,
                senderDoc.avatarUpdatedAt,
              )
            : null,
        };
      }
    }
    const trimmed = text.trim();
    const replyIdRaw =
      typeof replyToMessageId === 'string' && replyToMessageId.trim()
        ? replyToMessageId.trim()
        : null;
    const imgRaw =
      typeof imageFileId === 'string' && imageFileId.trim()
        ? imageFileId.trim()
        : null;
    const imgArr = Array.isArray(imageFileIds)
      ? imageFileIds
          .map((x) => x.trim())
          .filter(Boolean)
          .slice(0, 12)
      : [];
    const fileRaw =
      typeof fileFileId === 'string' && fileFileId.trim()
        ? fileFileId.trim()
        : null;
    if (!trimmed && !imgRaw && imgArr.length === 0 && !fileRaw) {
      throw new BadRequestException('Message text or attachment is required');
    }
    if (fileRaw && (imgRaw || imgArr.length > 0)) {
      throw new BadRequestException(
        'Cannot attach images and file in one message',
      );
    }
    let replyTarget: TeamForumMessageDocument | null = null;
    if (replyIdRaw) {
      if (!Types.ObjectId.isValid(replyIdRaw)) {
        throw new BadRequestException('Invalid reply target');
      }
      replyTarget = await this.messageModel.findOne({
        _id: new Types.ObjectId(replyIdRaw),
        teamId: teamOid,
        topicId: topOid,
      });
      if (!replyTarget || replyTarget.deletedAt) {
        throw new BadRequestException('Reply target not found');
      }
    }
    const senderDoc = await this.userModel.findById(userId).exec();
    if (!senderDoc) {
      throw new ForbiddenException('User not found');
    }
    const migrated = await this.gameIdentities.ensureMigrated(senderDoc);
    const username = this.gameIdentities.resolveSenderUsername(migrated);
    const senderRole = this.teams.resolveSquadRoleForMember(team, userId);
    const senderTeamTag = senderDoc.teamTag ?? null;
    const senderServerNumber =
      this.gameIdentities.resolveSenderServerNumber(migrated);
    if (trimmed) {
      assertStickerPayload(trimmed);
      await this.stickerAccess.assertUserMaySendStickerMessage(
        senderDoc,
        trimmed,
      );
    }
    const albumMetas: Array<{
      fileId: Types.ObjectId;
      mimeType: string;
      size: number;
    }> = [];
    if (imgArr.length > 0) {
      albumMetas.push(
        ...(await Promise.all(
          imgArr.map((fid) =>
            this.teamNewsAttachments.assertForumAttachmentForSender(
              teamOid,
              fid,
              userId,
            ),
          ),
        )),
      );
    }
    // Legacy single-file path (kept for old clients).
    const legacyMeta =
      imgRaw && albumMetas.length === 0
        ? await this.teamNewsAttachments.assertForumAttachmentForSender(
            teamOid,
            imgRaw,
            userId,
          )
        : null;
    const fileMeta = fileRaw
      ? await this.teamNewsAttachments.assertForumFileAttachmentForSender(
          teamOid,
          fileRaw,
          userId,
        )
      : null;
    let doc: TeamForumMessageDocument;
    try {
      doc = await this.messageModel.create({
        topicId: topOid,
        teamId: teamOid,
        senderUserId: userId,
        senderUsername: username,
        senderRole,
        senderTeamTag,
        senderServerNumber,
        text: trimmed,
        replyToMessageId: replyTarget?._id ?? null,
        imageFileId: legacyMeta?.fileId ?? null,
        imageFileIds: albumMetas.map((m) => m.fileId),
        imageMimeType: legacyMeta?.mimeType ?? null,
        imageSize: legacyMeta?.size ?? null,
        fileFileId: fileMeta?.fileId ?? null,
        fileFilename: fileMeta?.filename ?? null,
        editedAt: null,
        deletedAt: null,
        deletedByUserId: null,
        forwardedFrom: null,
        clientMessageId: normalizedClientMessageId,
      });
    } catch (err) {
      this.rethrowMongooseValidation(err);
    }
    topic.lastMessageAt = doc.createdAt ?? new Date();
    topic.messageCount = (topic.messageCount ?? 0) + 1;
    topic.lastMessageId = doc._id;
    topic.lastMessageSenderUserId = doc.senderUserId;
    topic.lastMessageSenderUsername = doc.senderUsername;
    await topic.save();
    await this.invalidateUnreadSumCacheForTeam(teamId);
    const row = this.messageRow(doc, replyTarget, userId);
    return {
      ...row,
      senderAvatarRelativeUrl: buildAvatarRelativeUrl(
        senderDoc._id.toString(),
        senderDoc.avatarKey,
        senderDoc.avatarUpdatedAt,
      ),
    };
  }

  async forwardMessage(
    teamId: string,
    topicId: string,
    userId: string,
    sourceMessageId: string,
  ): Promise<TeamForumMessageRow> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);

    if (!Types.ObjectId.isValid(sourceMessageId)) {
      throw new BadRequestException('Invalid source message id');
    }
    const srcOid = new Types.ObjectId(sourceMessageId);

    const source = await this.messageModel
      .findOne({
        _id: srcOid,
        teamId: teamOid,
        topicId: topOid,
        deletedAt: null,
      })
      .exec();
    if (!source) {
      throw new NotFoundException('Source message not found');
    }

    const actor = await this.userModel.findById(userId).exec();
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    const actorRole =
      this.teams.getSquadRoleForUser(team, userId) ?? PlayerTeamMemberRole.R1;
    const actorTeamTag = actor.teamTag ?? null;

    const fwdText = (source.text ?? '').trim();
    assertStickerPayload(fwdText);
    await this.stickerAccess.assertUserMaySendStickerMessage(actor, fwdText);

    const sourceRole =
      source.senderRole ??
      this.teams.getSquadRoleForUser(team, source.senderUserId) ??
      PlayerTeamMemberRole.R1;

    let sourceTeamTag = source.senderTeamTag ?? null;
    if (sourceTeamTag == null) {
      const srcUser = await this.userModel
        .findById(source.senderUserId)
        .select('teamTag')
        .lean<{ teamTag?: string | null }>()
        .exec();
      sourceTeamTag = srcUser?.teamTag ?? null;
    }

    // Prefer album ids; legacy single-file path is preserved for backward compatibility.
    const doc = await this.messageModel.create({
      topicId: topOid,
      teamId: teamOid,
      senderUserId: userId,
      senderUsername: this.gameIdentities.resolveSenderUsername(
        await this.gameIdentities.ensureMigrated(actor),
      ),
      senderRole: actorRole,
      senderTeamTag: actorTeamTag,
      senderServerNumber: this.gameIdentities.resolveSenderServerNumber(
        await this.gameIdentities.ensureMigrated(actor),
      ),
      text: fwdText,
      replyToMessageId: null,
      imageFileId: source.imageFileId ?? null,
      imageFileIds: source.imageFileIds ?? [],
      imageMimeType: source.imageMimeType ?? null,
      imageSize: source.imageSize ?? null,
      editedAt: null,
      deletedAt: null,
      deletedByUserId: null,
      forwardedFrom: {
        messageId: srcOid,
        senderUserId: source.senderUserId,
        senderUsername: source.senderUsername,
        senderRole: sourceRole,
        senderTeamTag: sourceTeamTag,
        senderServerNumber: source.senderServerNumber ?? null,
      },
    });

    // Topic stats.
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (topic) {
      topic.lastMessageAt = doc.createdAt ?? new Date();
      topic.messageCount = (topic.messageCount ?? 0) + 1;
      topic.lastMessageId = doc._id;
      topic.lastMessageSenderUserId = doc.senderUserId;
      topic.lastMessageSenderUsername = doc.senderUsername;
      await topic.save();
      await this.invalidateUnreadSumCacheForTeam(teamId);
    }

    const rows = await this.enrichMessagesWithAvatars([
      this.messageRow(doc, null, userId),
    ]);
    return rows[0];
  }

  private async assertMayEditMessage(
    teamId: string,
    msg: TeamForumMessageDocument,
    userId: string,
  ): Promise<void> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (msg.senderUserId === userId) {
      return;
    }
    const role = this.teams.getSquadRoleForUser(team, userId);
    if (isSquadOfficerRole(role)) {
      return;
    }
    throw new ForbiddenException('Not allowed to edit this message');
  }

  async patchMessage(
    teamId: string,
    topicId: string,
    messageId: string,
    userId: string,
    text: string,
  ): Promise<{
    message: TeamForumMessageRow;
    pinChanged: TeamForumTopicPinChangedPayload | null;
  }> {
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const msgOid = new Types.ObjectId(messageId);
    const msg = await this.messageModel.findOne({
      _id: msgOid,
      topicId: topOid,
      teamId: teamOid,
    });
    if (!msg || msg.deletedAt) {
      throw new NotFoundException('Message not found');
    }
    await this.assertMayEditMessage(teamId, msg, userId);
    const trimmed = text.trim();
    const hasImage = msg.imageFileId != null;
    if (!trimmed && !hasImage) {
      throw new BadRequestException('Message text is required');
    }
    assertStickerPayload(trimmed);
    const editorDoc = await this.userModel.findById(userId).exec();
    if (!editorDoc) {
      throw new ForbiddenException('User not found');
    }
    await this.stickerAccess.assertUserMaySendStickerMessage(
      editorDoc,
      trimmed,
    );
    msg.text = trimmed;
    msg.editedAt = new Date();
    await msg.save();
    const rows = await this.enrichMessagesWithAvatars([
      this.messageRow(msg, null, userId),
    ]);
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    let pinChanged: TeamForumTopicPinChangedPayload | null = null;
    if (topic) {
      const history = this.pinHistoryForTopic(topic);
      if (history.some((h) => h.messageId.toString() === messageId)) {
        pinChanged = await this.topicPinPayloadFromDoc(teamId, topicId, topic);
      }
    }
    return { message: rows[0], pinChanged };
  }

  async deleteMessage(
    teamId: string,
    topicId: string,
    messageId: string,
    userId: string,
  ): Promise<TeamForumTopicPinChangedPayload | null> {
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const msgOid = new Types.ObjectId(messageId);
    const msg = await this.messageModel.findOne({
      _id: msgOid,
      topicId: topOid,
      teamId: teamOid,
      deletedAt: null,
    });
    if (!msg) {
      throw new NotFoundException('Message not found');
    }
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    await this.assertMayEditMessage(teamId, msg, userId);
    const res = await this.messageModel.deleteOne({
      _id: msgOid,
      topicId: topOid,
      teamId: teamOid,
    });
    if (res.deletedCount !== 1) {
      throw new NotFoundException('Message not found');
    }
    await this.decrementTopicMessageCount(topOid, teamOid, 1);
    return this.clearTopicPinIfMessage(teamOid, topOid, messageId);
  }

  async bulkDeleteMessages(
    teamId: string,
    topicId: string,
    messageIds: string[],
    userId: string,
  ): Promise<{ deletedIds: string[]; pinChanged: TeamForumTopicPinChangedPayload | null }> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const uniq = [...new Set(messageIds.map((x) => x.trim()).filter(Boolean))];
    const valid = uniq.filter((id) => Types.ObjectId.isValid(id));
    if (valid.length === 0) {
      return { deletedIds: [], pinChanged: null };
    }

    const docs = await this.messageModel.find({
      _id: { $in: valid.map((id) => new Types.ObjectId(id)) },
      teamId: teamOid,
      topicId: topOid,
      deletedAt: null,
    });

    for (const msg of docs) {
      await this.assertMayEditMessage(teamId, msg, userId);
    }
    if (docs.length === 0) {
      return { deletedIds: [], pinChanged: null };
    }

    const topic = await this.topicModel.findOne({ _id: topOid, teamId: teamOid });
    const pinnedId = topic?.pinnedMessageId?.toString();

    const ids = docs.map((m) => m._id);
    const res = await this.messageModel.deleteMany({
      _id: { $in: ids },
      teamId: teamOid,
      topicId: topOid,
    });
    if (res.deletedCount > 0) {
      await this.decrementTopicMessageCount(topOid, teamOid, res.deletedCount);
    }
    const deletedIds = docs.map((m) => m._id.toString());
    let pinChanged: TeamForumTopicPinChangedPayload | null = null;
    if (pinnedId && deletedIds.includes(pinnedId)) {
      pinChanged = await this.clearTopicPinIfMessage(teamOid, topOid, pinnedId);
    }
    return { deletedIds, pinChanged };
  }

  private async decrementTopicMessageCount(
    topicId: Types.ObjectId,
    teamId: Types.ObjectId,
    count: number,
  ): Promise<void> {
    if (count <= 0) return;
    await this.topicModel.updateOne(
      { _id: topicId, teamId },
      { $inc: { messageCount: -count } },
    );
    await this.topicModel.updateOne(
      { _id: topicId, teamId, messageCount: { $lt: 0 } },
      { $set: { messageCount: 0 } },
    );
  }
}
