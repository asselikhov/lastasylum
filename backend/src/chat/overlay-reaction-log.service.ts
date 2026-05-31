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

export type OverlayReactionLogView = {
  _id: string;
  senderUserId: string;
  senderUsername: string;
  targetUserId: string | null;
  targetUsername: string | null;
  reaction: string;
  visibility: 'personal' | 'broadcast';
  createdAt: string;
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

  private toView(row: {
    _id: Types.ObjectId;
    senderUserId: string;
    senderUsername: string;
    targetUserId?: string | null;
    targetUsername?: string | null;
    reaction: string;
    visibility: 'personal' | 'broadcast';
    createdAt?: Date;
  }): OverlayReactionLogView {
    return {
      _id: row._id.toString(),
      senderUserId: row.senderUserId,
      senderUsername: row.senderUsername,
      targetUserId: row.targetUserId ?? null,
      targetUsername: row.targetUsername ?? null,
      reaction: row.reaction,
      visibility: row.visibility,
      createdAt: row.createdAt?.toISOString() ?? new Date().toISOString(),
    };
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
  }): Promise<OverlayReactionLogView> {
    const teamId = input.sender.playerTeamId;
    if (!teamId) {
      throw new BadRequestException('Sender has no team');
    }
    const doc = await this.logModel.create({
      teamId,
      senderUserId: input.sender._id.toString(),
      senderUsername: input.sender.username?.trim() || 'Союзник',
      targetUserId: input.target._id.toString(),
      targetUsername: input.target.username?.trim() || null,
      reaction: input.reaction,
      visibility: 'personal',
    });
    return this.toView(doc);
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
    });
    return this.toView(doc);
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
    const items = rows.map((r) =>
      this.toView(r as Parameters<typeof this.toView>[0]),
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
}
