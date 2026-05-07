import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { PlayerTeamMemberRole } from '../common/enums/player-team-member-role.enum';
import { User } from './schemas/user.schema';
import {
  TeamForumMessage,
  TeamForumMessageDocument,
} from './schemas/team-forum-message.schema';
import {
  TeamForumTopic,
  TeamForumTopicDocument,
} from './schemas/team-forum-topic.schema';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { TeamsService } from './teams.service';

export type TeamForumTopicRow = {
  id: string;
  teamId: string;
  title: string;
  createdByUserId: string;
  messageCount: number;
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
  text: string;
  editedAt: string | null;
  deletedAt: string | null;
  deletedByUserId: string | null;
  imageRelativeUrl: string | null;
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
    @InjectModel(User.name) private readonly userModel: Model<User>,
    private readonly teams: TeamsService,
    private readonly teamNewsAttachments: TeamNewsAttachmentsService,
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

  private topicRow(doc: TeamForumTopicDocument): TeamForumTopicRow {
    return {
      id: doc._id.toString(),
      teamId: doc.teamId.toString(),
      title: doc.title,
      createdByUserId: doc.createdByUserId,
      messageCount: doc.messageCount,
      lastMessageAt: doc.lastMessageAt ? doc.lastMessageAt.toISOString() : null,
      createdAt: doc.createdAt?.toISOString() ?? new Date().toISOString(),
      updatedAt: doc.updatedAt?.toISOString() ?? new Date().toISOString(),
    };
  }

  messageRow(doc: TeamForumMessageDocument): TeamForumMessageRow {
    const teamIdStr = doc.teamId.toString();
    const hasImage =
      !doc.deletedAt &&
      doc.imageFileId != null &&
      Types.ObjectId.isValid(doc.imageFileId.toString());
    return {
      id: doc._id.toString(),
      topicId: doc.topicId.toString(),
      teamId: teamIdStr,
      senderUserId: doc.senderUserId,
      senderUsername: doc.senderUsername,
      text: doc.deletedAt ? '' : doc.text,
      editedAt: doc.editedAt ? doc.editedAt.toISOString() : null,
      deletedAt: doc.deletedAt ? doc.deletedAt.toISOString() : null,
      deletedByUserId: doc.deletedByUserId,
      imageRelativeUrl: hasImage
        ? `/teams/${teamIdStr}/news/attachments/${doc.imageFileId!.toString()}`
        : null,
      createdAt: doc.createdAt?.toISOString() ?? new Date().toISOString(),
      updatedAt: doc.updatedAt?.toISOString() ?? new Date().toISOString(),
    };
  }

  async listTopics(teamId: string, userId: string): Promise<TeamForumTopicRow[]> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const tid = new Types.ObjectId(teamId);
    const rows = await this.topicModel
      .find({ teamId: tid })
      .sort({ lastMessageAt: -1, updatedAt: -1 })
      .lean();
    return rows.map((r) =>
      this.topicRow(r as unknown as TeamForumTopicDocument),
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

  async listMessages(
    teamId: string,
    topicId: string,
    userId: string,
    before?: string,
    limitRaw?: number,
  ): Promise<TeamForumMessageRow[]> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
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
    };
    if (before && Types.ObjectId.isValid(before)) {
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    const rows = await this.messageModel
      .find(filter)
      .sort({ _id: -1 })
      .limit(limit)
      .lean();
    return rows
      .map((r) => this.messageRow(r as unknown as TeamForumMessageDocument))
      .reverse();
  }

  async postMessage(
    teamId: string,
    topicId: string,
    userId: string,
    text: string,
    imageFileId?: string | null,
  ): Promise<TeamForumMessageRow> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const teamOid = new Types.ObjectId(teamId);
    const topOid = new Types.ObjectId(topicId);
    const topic = await this.topicModel.findOne({
      _id: topOid,
      teamId: teamOid,
    });
    if (!topic) {
      throw new NotFoundException('Topic not found');
    }
    const user = await this.userModel.findById(userId).lean();
    const username =
      user && typeof user.username === 'string' ? user.username : userId;
    const trimmed = text.trim();
    const imgRaw =
      typeof imageFileId === 'string' && imageFileId.trim()
        ? imageFileId.trim()
        : null;
    if (!trimmed && !imgRaw) {
      throw new BadRequestException('Message text or image is required');
    }
    let imageMeta: {
      fileId: Types.ObjectId;
      mimeType: string;
      size: number;
    } | null = null;
    if (imgRaw) {
      imageMeta = await this.teamNewsAttachments.assertForumAttachmentForSender(
        teamOid,
        imgRaw,
        userId,
      );
    }
    const doc = await this.messageModel.create({
      topicId: topOid,
      teamId: teamOid,
      senderUserId: userId,
      senderUsername: username,
      text: trimmed,
      imageFileId: imageMeta?.fileId ?? null,
      imageMimeType: imageMeta?.mimeType ?? null,
      imageSize: imageMeta?.size ?? null,
      editedAt: null,
      deletedAt: null,
      deletedByUserId: null,
    });
    topic.lastMessageAt = doc.createdAt ?? new Date();
    topic.messageCount = (topic.messageCount ?? 0) + 1;
    await topic.save();
    return this.messageRow(doc);
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
    msg.text = trimmed;
    msg.editedAt = new Date();
    await msg.save();
    return this.messageRow(msg);
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
    });
    if (!msg || msg.deletedAt) {
      throw new NotFoundException('Message not found');
    }
    await this.assertMayEditMessage(teamId, msg, userId);
    msg.deletedAt = new Date();
    msg.deletedByUserId = userId;
    await msg.save();
  }
}
