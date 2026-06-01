import {
  BadRequestException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UsersService } from '../users/users.service';
import { ChatService } from './chat.service';
import {
  OverlayReactionLog,
  OverlayReactionLogDocument,
} from './schemas/overlay-reaction-log.schema';
import {
  OverlayReactionLogReadState,
  OverlayReactionLogReadStateDocument,
} from './schemas/overlay-reaction-log-read-state.schema';

export type OverlayReactionLogReactionView = {
  emoji: string;
  count: number;
  reactedByMe: boolean;
};

export type OverlayReactionLogReplyToView = {
  _id: string;
  reaction: string;
  visibility: 'personal' | 'broadcast';
  senderUserId: string;
  senderUsername: string;
  targetUserId: string | null;
  targetUsername: string | null;
};

export type OverlayReactionLogView = {
  _id: string;
  senderUserId: string;
  senderUsername: string;
  targetUserId: string | null;
  targetUsername: string | null;
  reaction: string;
  visibility: 'personal' | 'broadcast';
  createdAt: string;
  reactions: OverlayReactionLogReactionView[];
  replyToLogId: string | null;
  replyToLog: OverlayReactionLogReplyToView | null;
};

type UserLean = {
  _id: Types.ObjectId;
  username?: string;
  playerTeamId?: Types.ObjectId | null;
  membershipStatus?: TeamMembershipStatus;
};

@Injectable()
export class OverlayReactionLogService {
  constructor(
    @InjectModel(OverlayReactionLog.name)
    private readonly logModel: Model<OverlayReactionLogDocument>,
    @InjectModel(OverlayReactionLogReadState.name)
    private readonly readStateModel: Model<OverlayReactionLogReadStateDocument>,
    private readonly usersService: UsersService,
    private readonly chatService: ChatService,
  ) {}

  private replyToLogFromRow(row: {
    replyToLogId?: Types.ObjectId | null;
    replyToLog?: {
      _id: Types.ObjectId;
      reaction: string;
      visibility: 'personal' | 'broadcast';
      senderUserId: string;
      senderUsername: string;
      targetUserId?: string | null;
      targetUsername?: string | null;
    } | null;
  }): OverlayReactionLogReplyToView | null {
    const snap = row.replyToLog;
    if (!snap?._id) return null;
    return {
      _id: snap._id.toString(),
      reaction: snap.reaction,
      visibility: snap.visibility,
      senderUserId: snap.senderUserId,
      senderUsername: snap.senderUsername,
      targetUserId: snap.targetUserId ?? null,
      targetUsername: snap.targetUsername ?? null,
    };
  }

  private toView(
    row: {
      _id: Types.ObjectId;
      senderUserId: string;
      senderUsername: string;
      targetUserId?: string | null;
      targetUsername?: string | null;
      reaction: string;
      visibility: 'personal' | 'broadcast';
      createdAt?: Date;
      reactions?: { emoji: string; userIds: string[] }[] | null;
      replyToLogId?: Types.ObjectId | null;
      replyToLog?: {
        _id: Types.ObjectId;
        reaction: string;
        visibility: 'personal' | 'broadcast';
        senderUserId: string;
        senderUsername: string;
        targetUserId?: string | null;
        targetUsername?: string | null;
      } | null;
    },
    viewerUserId?: string,
  ): OverlayReactionLogView {
    const reactions = (row.reactions ?? []).map((r) => {
      const userIds = r.userIds ?? [];
      return {
        emoji: r.emoji,
        count: userIds.length,
        reactedByMe: viewerUserId
          ? userIds.includes(viewerUserId)
          : false,
      };
    });
    return {
      _id: row._id.toString(),
      senderUserId: row.senderUserId,
      senderUsername: row.senderUsername,
      targetUserId: row.targetUserId ?? null,
      targetUsername: row.targetUsername ?? null,
      reaction: row.reaction,
      visibility: row.visibility,
      createdAt: row.createdAt?.toISOString() ?? new Date().toISOString(),
      reactions,
      replyToLogId: row.replyToLogId?.toString() ?? null,
      replyToLog: this.replyToLogFromRow(row),
    };
  }

  private buildReplyToSnapshot(parent: {
    _id: Types.ObjectId;
    senderUserId: string;
    senderUsername: string;
    targetUserId?: string | null;
    targetUsername?: string | null;
    reaction: string;
    visibility: 'personal' | 'broadcast';
  }) {
    return {
      _id: parent._id,
      reaction: parent.reaction,
      visibility: parent.visibility,
      senderUserId: parent.senderUserId,
      senderUsername: parent.senderUsername,
      targetUserId: parent.targetUserId ?? null,
      targetUsername: parent.targetUsername ?? null,
    };
  }

  /** Legacy rows may have replyToLogId without embedded snapshot. */
  private async hydrateMissingReplySnapshots(
    rows: Array<{
      replyToLogId?: Types.ObjectId | null;
      replyToLog?: Parameters<typeof this.buildReplyToSnapshot>[0] | null;
    }>,
  ): Promise<void> {
    const missingIds = [
      ...new Set(
        rows
          .filter((row) => row.replyToLogId && !row.replyToLog?._id)
          .map((row) => row.replyToLogId!.toString()),
      ),
    ];
    if (missingIds.length === 0) return;
    const parents = await this.logModel
      .find({
        _id: { $in: missingIds.map((id) => new Types.ObjectId(id)) },
      })
      .lean()
      .exec();
    const byId = new Map(
      parents.map((p) => [p._id.toString(), p as Parameters<typeof this.buildReplyToSnapshot>[0]]),
    );
    for (const row of rows) {
      if (row.replyToLog?._id) continue;
      const parentId = row.replyToLogId?.toString();
      if (!parentId) continue;
      const parent = byId.get(parentId);
      if (parent) {
        row.replyToLog = this.buildReplyToSnapshot(parent);
      }
    }
  }

  private async loadParentLogForReply(
    teamId: Types.ObjectId,
    replierUserId: string,
    replyToLogId: string,
  ): Promise<OverlayReactionLogDocument> {
    const trimmed = replyToLogId?.trim();
    if (!trimmed || !Types.ObjectId.isValid(trimmed)) {
      throw new BadRequestException('Invalid replyToLogId');
    }
    const parent = await this.logModel
      .findOne({
        _id: new Types.ObjectId(trimmed),
        teamId,
      })
      .exec();
    if (!parent) {
      throw new NotFoundException('Reaction log entry not found');
    }
    const parentFilter = this.visibilityFilter(replierUserId);
    const visible = await this.logModel
      .findOne({ _id: parent._id, teamId, ...parentFilter })
      .lean()
      .exec();
    if (!visible) {
      throw new BadRequestException('Cannot reply to this reaction log entry');
    }
    return parent;
  }

  private async assertActiveTeamMember(userId: string): Promise<{
    userId: string;
    teamId: Types.ObjectId;
  }> {
    await this.chatService.assertUserMayUseChat(userId);
    const user = await this.usersService.findById(userId);
    if (!user?.playerTeamId) {
      throw new NotFoundException('Team not found');
    }
    if (
      this.usersService.effectiveMembership(user) !==
      TeamMembershipStatus.ACTIVE
    ) {
      throw new BadRequestException('Reaction log is not available');
    }
    return { userId, teamId: user.playerTeamId };
  }

  private visibilityFilter(userId: string): Record<string, unknown> {
    return {
      $or: [
        { visibility: 'broadcast' },
        {
          visibility: 'personal',
          $or: [{ senderUserId: userId }, { targetUserId: userId }],
        },
      ],
    };
  }

  async createPersonal(input: {
    sender: UserLean;
    target: UserLean;
    reaction: string;
    replyToLogId?: string | null;
  }): Promise<{ entry: OverlayReactionLogView; recipientUserIds: string[] }> {
    const teamId = input.sender.playerTeamId;
    if (!teamId) {
      throw new BadRequestException('Sender has no team');
    }
    const senderId = input.sender._id.toString();
    const targetId = input.target._id.toString();
    const replyToLogId = input.replyToLogId?.trim();

    if (replyToLogId) {
      const parent = await this.loadParentLogForReply(
        teamId,
        senderId,
        replyToLogId,
      );
      const parentSenderId = parent.senderUserId.trim();
      const parentTargetId = parent.targetUserId?.trim() ?? '';

      if (parent.visibility === 'broadcast') {
        if (senderId === parentSenderId) {
          throw new BadRequestException('Cannot reply to your own broadcast reaction');
        }
        if (targetId !== parentSenderId) {
          throw new BadRequestException('Invalid reply target for broadcast reaction');
        }
      } else {
        if (senderId !== parentTargetId) {
          throw new BadRequestException('Only the recipient can reply to this reaction');
        }
        if (targetId !== parentSenderId) {
          throw new BadRequestException('Invalid reply target for personal reaction');
        }
      }

      const entryVisibility: 'personal' | 'broadcast' =
        parent.visibility === 'broadcast' ? 'broadcast' : 'personal';
      const replySnapshot = this.buildReplyToSnapshot(parent.toObject());

      const doc = await this.logModel.create({
        teamId,
        senderUserId: senderId,
        senderUsername: input.sender.username?.trim() || 'Союзник',
        targetUserId: targetId,
        targetUsername: input.target.username?.trim() || null,
        reaction: input.reaction,
        visibility: entryVisibility,
        replyToLogId: parent._id,
        replyToLog: replySnapshot,
        reactions: [],
      });
      const entry = this.toView(doc.toObject(), senderId);
      const recipientUserIds = await this.listRecipientUserIds(teamId, doc);
      return { entry, recipientUserIds };
    }

    const doc = await this.logModel.create({
      teamId,
      senderUserId: senderId,
      senderUsername: input.sender.username?.trim() || 'Союзник',
      targetUserId: targetId,
      targetUsername: input.target.username?.trim() || null,
      reaction: input.reaction,
      visibility: 'personal',
      replyToLogId: null,
      replyToLog: null,
      reactions: [],
    });
    const entry = this.toView(doc.toObject(), senderId);
    const recipientUserIds = await this.listRecipientUserIds(teamId, doc);
    return { entry, recipientUserIds };
  }

  async createBroadcast(input: {
    sender: UserLean;
    reaction: string;
  }): Promise<OverlayReactionLogView> {
    const teamId = input.sender.playerTeamId;
    if (!teamId) {
      throw new BadRequestException('Sender has no team');
    }
    const doc = await this.logModel.create({
      teamId,
      senderUserId: input.sender._id.toString(),
      senderUsername: input.sender.username?.trim() || 'Союзник',
      targetUserId: null,
      targetUsername: null,
      reaction: input.reaction,
      visibility: 'broadcast',
      replyToLogId: null,
      replyToLog: null,
      reactions: [],
    });
    return this.toView(doc.toObject(), input.sender._id.toString());
  }

  async listForViewer(
    userId: string,
    options?: { before?: string; limit?: number },
  ): Promise<{ items: OverlayReactionLogView[]; nextCursor: string | null }> {
    const { teamId } = await this.assertActiveTeamMember(userId);
    const lim = Math.min(50, Math.max(1, options?.limit ?? 50));
    const filter: Record<string, unknown> = {
      teamId,
      ...this.visibilityFilter(userId),
    };
    const before = options?.before?.trim();
    if (before) {
      if (!Types.ObjectId.isValid(before)) {
        throw new BadRequestException('Invalid before cursor');
      }
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    const rows = await this.logModel
      .find(filter)
      .sort({ _id: -1 })
      .limit(lim)
      .lean()
      .exec();
    await this.hydrateMissingReplySnapshots(
      rows as Array<{
        replyToLogId?: Types.ObjectId | null;
        replyToLog?: Parameters<typeof this.buildReplyToSnapshot>[0] | null;
      }>,
    );
    const items = rows.map((r) =>
      this.toView(r as Parameters<typeof this.toView>[0], userId),
    );
    const nextCursor =
      items.length >= lim ? items[items.length - 1]._id : null;
    return { items, nextCursor };
  }

  async getReadCursor(
    userId: string,
  ): Promise<{ lastSeenLogId: string | null }> {
    const { teamId } = await this.assertActiveTeamMember(userId);
    const row = await this.readStateModel
      .findOne({ teamId, userId })
      .lean()
      .exec();
    return { lastSeenLogId: row?.lastSeenLogId ?? null };
  }

  async advanceReadCursor(
    userId: string,
    lastSeenLogId: string,
  ): Promise<{ lastSeenLogId: string }> {
    const trimmed = lastSeenLogId?.trim();
    if (!trimmed || !Types.ObjectId.isValid(trimmed)) {
      throw new BadRequestException('Invalid lastSeenLogId');
    }
    const { teamId } = await this.assertActiveTeamMember(userId);
    const existing = await this.readStateModel
      .findOne({ teamId, userId })
      .lean()
      .exec();
    const prev = existing?.lastSeenLogId?.trim();
    let next = trimmed;
    if (prev && Types.ObjectId.isValid(prev)) {
      const prevOid = new Types.ObjectId(prev);
      const nextOid = new Types.ObjectId(trimmed);
      next = nextOid.getTimestamp() >= prevOid.getTimestamp() ? trimmed : prev;
    }
    await this.readStateModel
      .updateOne(
        { teamId, userId },
        { $set: { lastSeenLogId: next } },
        { upsert: true },
      )
      .exec();
    return { lastSeenLogId: next };
  }

  async countUnread(userId: string): Promise<number> {
    const { teamId } = await this.assertActiveTeamMember(userId);
    const filter: Record<string, unknown> = {
      teamId,
      senderUserId: { $ne: userId },
      ...this.visibilityFilter(userId),
    };
    const row = await this.readStateModel
      .findOne({ teamId, userId })
      .lean()
      .exec();
    const lastSeen = row?.lastSeenLogId?.trim();
    if (lastSeen && Types.ObjectId.isValid(lastSeen)) {
      filter._id = { $gt: new Types.ObjectId(lastSeen) };
    }
    return this.logModel.countDocuments(filter).exec();
  }

  async toggleLogEntryReaction(
    userId: string,
    logId: string,
    emoji: string,
  ): Promise<{ entry: OverlayReactionLogView; recipientUserIds: string[] }> {
    const trimmedId = logId?.trim();
    if (!trimmedId || !Types.ObjectId.isValid(trimmedId)) {
      throw new BadRequestException('Invalid log id');
    }
    const trimmedEmoji = emoji?.trim();
    if (!trimmedEmoji) {
      throw new BadRequestException('emoji is required');
    }
    const { teamId } = await this.assertActiveTeamMember(userId);
    const row = await this.logModel
      .findOne({
        _id: new Types.ObjectId(trimmedId),
        teamId,
        ...this.visibilityFilter(userId),
      })
      .exec();
    if (!row) {
      throw new NotFoundException('Reaction log entry not found');
    }
    const list = (row.reactions ?? []) as {
      emoji: string;
      userIds: string[];
    }[];
    const existing = list.find((x) => x.emoji === trimmedEmoji);
    if (!existing) {
      list.push({ emoji: trimmedEmoji, userIds: [userId] });
    } else {
      const idx = existing.userIds.indexOf(userId);
      if (idx >= 0) {
        existing.userIds.splice(idx, 1);
      } else {
        existing.userIds.push(userId);
      }
    }
    row.reactions = list.filter((x) => x.userIds.length > 0) as typeof row.reactions;
    await row.save();
    const entry = this.toView(row.toObject(), userId);
    const recipientUserIds = await this.listRecipientUserIds(teamId, row);
    return { entry, recipientUserIds };
  }

  async listRecipientUserIds(
    teamId: Types.ObjectId,
    row: {
      visibility: 'personal' | 'broadcast';
      senderUserId: string;
      targetUserId?: string | null;
    },
  ): Promise<string[]> {
    if (row.visibility === 'broadcast') {
      const members = await this.usersService.listActiveTeamMemberUserIds(
        teamId.toString(),
      );
      return members;
    }
    const ids = new Set<string>();
    ids.add(row.senderUserId);
    if (row.targetUserId?.trim()) {
      ids.add(row.targetUserId.trim());
    }
    return [...ids];
  }
}
