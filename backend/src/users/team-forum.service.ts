import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import mongoose, { Model, Types } from 'mongoose';
import { PlayerTeamMemberRole } from '../common/enums/player-team-member-role.enum';
import {
  parseZlobyakaStickerStem,
  ZLOBYAKA_STICKER_STEMS,
} from '../chat/zlobyaka-stickers.const';
import { User, UserDocument } from './schemas/user.schema';
import {
  TeamForumMessage,
  TeamForumMessageDocument,
} from './schemas/team-forum-message.schema';
import {
  TeamForumTopic,
  TeamForumTopicDocument,
} from './schemas/team-forum-topic.schema';
import {
  TeamForumTopicReadState,
} from './schemas/team-forum-topic-read-state.schema';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { GameIdentitiesService } from './game-identities.service';
import { TeamsService } from './teams.service';
import { StickerAccessService } from './sticker-access.service';

export type TeamForumTopicRow = {
  id: string;
  teamId: string;
  title: string;
  createdByUserId: string;
  messageCount: number;
  unreadCount: number;
  lastReadMessageId: string | null;
  lastMessageAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type TeamForumMessageRow = {
  id: string;
  topicId: string;
  teamId: string;
  senderUserId: string;
  senderUsername: string;
  senderRole: PlayerTeamMemberRole;
  senderTeamTag: string | null;
  senderServerNumber: number | null;
  text: string;
  replyToMessageId: string | null;
  replyTo:
    | {
        id: string;
        senderUsername: string;
        senderRole: PlayerTeamMemberRole;
        senderTeamTag: string | null;
        senderServerNumber: number | null;
        text: string;
      }
    | null;
  editedAt: string | null;
  deletedAt: string | null;
  deletedByUserId: string | null;
  imageRelativeUrl: string | null;
  imageRelativeUrls: string[];
  fileRelativeUrl: string | null;
  fileFilename: string | null;
  forwardedFrom:
    | {
        messageId: string;
        senderUserId: string;
        senderUsername: string;
        senderRole: PlayerTeamMemberRole;
        senderTeamTag: string | null;
        senderServerNumber: number | null;
      }
    | null;
  createdAt: string;
  updatedAt: string;
};

@Injectable()
export class TeamForumService {
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
  ) {}

  private async assertCanManageTopicsAsync(
    teamId: string,
    userId: string,
  ): Promise<void> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const role = this.teams.getSquadRoleForUser(team, userId);
    if (
      role !== PlayerTeamMemberRole.R4 &&
      role !== PlayerTeamMemberRole.R5
    ) {
      throw new ForbiddenException(
        'Only squad roles R4 and R5 can manage forum topics',
      );
    }
  }

  private topicRow(
    doc: TeamForumTopicDocument,
    extras?: {
      messageCount?: number;
      unreadCount?: number;
      lastReadMessageId?: string | null;
    },
  ): TeamForumTopicRow {
    return {
      id: doc._id.toString(),
      teamId: doc.teamId.toString(),
      title: doc.title,
      createdByUserId: doc.createdByUserId,
      messageCount: extras?.messageCount ?? doc.messageCount ?? 0,
      unreadCount: extras?.unreadCount ?? 0,
      lastReadMessageId: extras?.lastReadMessageId ?? null,
      lastMessageAt: doc.lastMessageAt ? doc.lastMessageAt.toISOString() : null,
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
      readStates.map((r) => [
        (r.topicId as Types.ObjectId).toString(),
        r.lastReadMessageId,
      ]),
    );
  }

  async getLastReadMessageIdsByTopicIds(
    userId: string,
    topicIds: Types.ObjectId[],
  ): Promise<Map<string, string>> {
    return this.readStatesByTopicIds(userId, topicIds);
  }

  private async countUnreadForumMessages(
    userId: string,
    topicIds: Types.ObjectId[],
  ): Promise<Map<string, number>> {
    const out = new Map<string, number>();
    if (topicIds.length === 0) return out;
    const readByTopic = await this.readStatesByTopicIds(userId, topicIds);
    await Promise.all(
      topicIds.map(async (topicOid) => {
        const key = topicOid.toString();
        const lastRead = readByTopic.get(key);
        const filter: Record<string, unknown> = {
          topicId: topicOid,
          deletedAt: null,
          senderUserId: { $ne: userId },
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

  async markTopicRead(
    teamId: string,
    topicId: string,
    userId: string,
    messageId: string,
  ): Promise<{ topicId: string; messageId: string }> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(topicId) || !Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid id');
    }
    const topOid = new Types.ObjectId(topicId);
    const teamOid = new Types.ObjectId(teamId);
    const topic = await this.topicModel.findOne({ _id: topOid, teamId: teamOid });
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
    const lastReadMessageId = advanced ? messageId : prev!;
    if (advanced) {
      await this.topicReadStateModel
        .updateOne(
          { topicId: topOid, userId },
          { $set: { lastReadMessageId: messageId } },
          { upsert: true },
        )
        .exec();
    }
    return { topicId, messageId: lastReadMessageId };
  }

  private assertZlobyakaStickerPayload(text: string): void {
    const stem = parseZlobyakaStickerStem(text);
    if (!stem) return;
    if (!ZLOBYAKA_STICKER_STEMS.has(stem)) {
      throw new BadRequestException('Unknown sticker');
    }
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

  messageRow(
    doc: TeamForumMessageDocument,
    replyTarget?: TeamForumMessageDocument | null,
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
            ? [`/teams/${teamIdStr}/news/attachments/${doc.imageFileId!.toString()}`]
            : [],
      fileRelativeUrl:
        !doc.deletedAt &&
        doc.fileFileId != null &&
        Types.ObjectId.isValid(doc.fileFileId.toString())
          ? `/teams/${teamIdStr}/news/attachments/${doc.fileFileId.toString()}`
          : null,
      fileFilename: doc.deletedAt ? null : doc.fileFilename ?? null,
      forwardedFrom: doc.forwardedFrom
        ? {
            messageId: doc.forwardedFrom.messageId.toString(),
            senderUserId: doc.forwardedFrom.senderUserId,
            senderUsername: doc.forwardedFrom.senderUsername,
            senderRole: doc.forwardedFrom.senderRole ?? PlayerTeamMemberRole.R1,
            senderTeamTag: doc.forwardedFrom.senderTeamTag ?? null,
            senderServerNumber:
              doc.forwardedFrom.senderServerNumber ?? null,
          }
        : null,
      createdAt: doc.createdAt?.toISOString() ?? new Date().toISOString(),
      updatedAt: doc.updatedAt?.toISOString() ?? new Date().toISOString(),
    };
  }

  async listTopicsForAdmin(teamId: string): Promise<TeamForumTopicRow[]> {
    await this.teams.assertTeamExistsForAdmin(teamId);
    const tid = new Types.ObjectId(teamId);
    const rows = await this.topicModel
      .find({ teamId: tid })
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .lean();
    const topicIds = rows.map(
      (r) => (r as { _id: Types.ObjectId })._id as Types.ObjectId,
    );
    const countAgg = await this.messageModel.aggregate<{
      _id: Types.ObjectId;
      count: number;
    }>([
      { $match: { teamId: tid, deletedAt: null } },
      { $group: { _id: '$topicId', count: { $sum: 1 } } },
    ]);
    const countMap = new Map(
      countAgg.map((c) => [c._id.toString(), c.count]),
    );
    return rows.map((r) => {
      const doc = r as unknown as TeamForumTopicDocument;
      const id = doc._id.toString();
      const actualCount = countMap.get(id) ?? 0;
      return this.topicRow(doc, {
        messageCount: actualCount,
        unreadCount: 0,
        lastReadMessageId: null,
      });
    });
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
    return docs.map((doc) => this.messageRow(doc, null));
  }

  async listTopics(teamId: string, userId: string): Promise<TeamForumTopicRow[]> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const tid = new Types.ObjectId(teamId);
    const rows = await this.topicModel
      .find({ teamId: tid })
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .lean();
    const topicIds = rows.map(
      (r) => (r as { _id: Types.ObjectId })._id as Types.ObjectId,
    );
    const countAgg = await this.messageModel.aggregate<{
      _id: Types.ObjectId;
      count: number;
    }>([
      { $match: { teamId: tid, deletedAt: null } },
      { $group: { _id: '$topicId', count: { $sum: 1 } } },
    ]);
    const countMap = new Map(
      countAgg.map((c) => [c._id.toString(), c.count]),
    );
    const unreadMap = await this.countUnreadForumMessages(userId, topicIds);
    const lastReadMap = await this.getLastReadMessageIdsByTopicIds(userId, topicIds);
    return rows.map((r) => {
      const doc = r as unknown as TeamForumTopicDocument;
      const id = doc._id.toString();
      const actualCount = countMap.get(id) ?? 0;
      if ((doc.messageCount ?? 0) !== actualCount) {
        void this.topicModel
          .updateOne({ _id: doc._id }, { $set: { messageCount: actualCount } })
          .exec();
      }
      return this.topicRow(doc, {
        messageCount: actualCount,
        unreadCount: unreadMap.get(id) ?? 0,
        lastReadMessageId: lastReadMap.get(id) ?? null,
      });
    });
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

  async listMessages(
    teamId: string,
    topicId: string,
    userId: string,
    before?: string,
    limitRaw?: number,
  ): Promise<TeamForumMessageRow[]> {
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

    // Backward compatibility: older forum messages may not have senderRole/senderTeamTag persisted.
    // Fill missing values from team membership + sender user docs so UI role badges stay consistent.
    const senderIds = [
      ...new Set(docs.map((d) => d.senderUserId).filter((x) => Boolean(x))),
    ];
    const senderTeamTagMap = new Map(
      (
        await this.userModel
          .find({ _id: { $in: senderIds.map((id) => new Types.ObjectId(id)) } })
          .select('teamTag')
          .lean<Array<{ _id: Types.ObjectId; teamTag?: string | null }>>()
          .exec()
      ).map((u) => [u._id.toString(), u.teamTag ?? null]),
    );
    for (const d of docs) {
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
    }
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

    // Same backward compatibility for reply documents (may be older and missing senderRole/senderTeamTag).
    if (replyDocs.length > 0) {
      const replySenderIds = [
        ...new Set(
          (replyDocs as unknown as TeamForumMessageDocument[]).map((d) => d.senderUserId),
        ),
      ];
      const replySenderTeamTagMap = new Map(
        (
          await this.userModel
            .find({
              _id: { $in: replySenderIds.map((id) => new Types.ObjectId(id)) },
            })
            .select('teamTag')
            .lean<Array<{ _id: Types.ObjectId; teamTag?: string | null }>>()
            .exec()
        ).map((u) => [u._id.toString(), u.teamTag ?? null]),
      );
      for (const d of replyDocs as unknown as TeamForumMessageDocument[]) {
        const needRole = !(d as any).senderRole;
        const needTag = (d as any).senderTeamTag == null;
        if (needRole) {
          (d as any).senderRole =
            this.teams.getSquadRoleForUser(team, d.senderUserId) ??
            PlayerTeamMemberRole.R1;
        }
        if (needTag) {
          (d as any).senderTeamTag =
            replySenderTeamTagMap.get(d.senderUserId) ?? null;
        }
      }
    }
    const replyMap = new Map(
      (replyDocs as unknown as TeamForumMessageDocument[]).map((d) => [
        d._id.toString(),
        d,
      ]),
    );
    return docs
      .map((d) => {
        const rid = this.asIdString(d.replyToMessageId);
        return this.messageRow(d, rid ? replyMap.get(rid) : null);
      })
      .reverse();
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
      ? imageFileIds.map((x) => x.trim()).filter(Boolean).slice(0, 12)
      : [];
    const fileRaw =
      typeof fileFileId === 'string' && fileFileId.trim()
        ? fileFileId.trim()
        : null;
    if (!trimmed && !imgRaw && imgArr.length === 0 && !fileRaw) {
      throw new BadRequestException('Message text or attachment is required');
    }
    if (fileRaw && (imgRaw || imgArr.length > 0)) {
      throw new BadRequestException('Cannot attach images and file in one message');
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
      this.assertZlobyakaStickerPayload(trimmed);
      await this.stickerAccess.assertUserMaySendStickerMessage(
        senderDoc as UserDocument,
        trimmed,
      );
    }
    const albumMetas: Array<{
      fileId: Types.ObjectId;
      mimeType: string;
      size: number;
    }> = [];
    if (imgArr.length > 0) {
      for (const fid of imgArr) {
        albumMetas.push(
          await this.teamNewsAttachments.assertForumAttachmentForSender(
            teamOid,
            fid,
            userId,
          ),
        );
      }
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
      });
    } catch (err) {
      this.rethrowMongooseValidation(err);
    }
    topic.lastMessageAt = doc.createdAt ?? new Date();
    topic.messageCount = (topic.messageCount ?? 0) + 1;
    await topic.save();
    return this.messageRow(doc, replyTarget);
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
      this.teams.getSquadRoleForUser(team, userId) ??
      PlayerTeamMemberRole.R1;
    const actorTeamTag = actor.teamTag ?? null;

    const fwdText = (source.text ?? '').trim();
    this.assertZlobyakaStickerPayload(fwdText);
    await this.stickerAccess.assertUserMaySendStickerMessage(
      actor,
      fwdText,
    );

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
      senderServerNumber:
        this.gameIdentities.resolveSenderServerNumber(
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
      await topic.save();
    }

    return this.messageRow(doc, null);
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
    if (role === PlayerTeamMemberRole.R5) {
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
  ): Promise<TeamForumMessageRow> {
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
    this.assertZlobyakaStickerPayload(trimmed);
    const editorDoc = await this.userModel.findById(userId).exec();
    if (!editorDoc) {
      throw new ForbiddenException('User not found');
    }
    await this.stickerAccess.assertUserMaySendStickerMessage(
      editorDoc as UserDocument,
      trimmed,
    );
    msg.text = trimmed;
    msg.editedAt = new Date();
    await msg.save();
    return this.messageRow(msg, null);
  }

  async deleteMessage(
    teamId: string,
    topicId: string,
    messageId: string,
    userId: string,
  ): Promise<void> {
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
  }

  async bulkDeleteMessages(
    teamId: string,
    topicId: string,
    messageIds: string[],
    userId: string,
  ): Promise<string[]> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const uniq = [...new Set(messageIds.map((x) => x.trim()).filter(Boolean))];
    const valid = uniq.filter((id) => Types.ObjectId.isValid(id));
    if (valid.length === 0) return [];

    const docs = await this.messageModel.find({
      _id: { $in: valid.map((id) => new Types.ObjectId(id)) },
      teamId: teamOid,
      topicId: topOid,
      deletedAt: null,
    });

    for (const msg of docs) {
      await this.assertMayEditMessage(teamId, msg, userId);
    }
    if (docs.length === 0) return [];

    const ids = docs.map((m) => m._id);
    const res = await this.messageModel.deleteMany({
      _id: { $in: ids },
      teamId: teamOid,
      topicId: topOid,
    });
    if (res.deletedCount > 0) {
      await this.decrementTopicMessageCount(topOid, teamOid, res.deletedCount);
    }
    return docs.map((m) => m._id.toString());
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
