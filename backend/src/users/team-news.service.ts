import { randomUUID } from 'crypto';
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
import { TeamNews, TeamNewsDocument } from './schemas/team-news.schema';
import { CreateTeamNewsDto } from './dto/create-team-news.dto';
import { UpdateTeamNewsDto } from './dto/update-team-news.dto';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { TeamsService } from './teams.service';
import type { PlayerTeamDocument } from './schemas/player-team.schema';

export type TeamNewsListRow = {
  id: string;
  teamId: string;
  title: string;
  excerpt: string;
  authorUserId: string;
  authorUsername: string;
  createdAt: string;
  updatedAt: string;
  hasPoll: boolean;
  firstImageRelativeUrl: string | null;
  pollTallies: Array<{ optionId: string; count: number }>;
  myVoteOptionId: string | null;
};

export type TeamNewsDetail = TeamNewsListRow & {
  body: string;
  imageRelativeUrls: string[];
  poll: null | {
    question: string;
    options: Array<{ id: string; text: string }>;
    votes: Array<{ userId: string; optionId: string }>;
    tallies: Array<{ optionId: string; count: number }>;
    myVoteOptionId: string | null;
  };
};

@Injectable()
export class TeamNewsService {
  constructor(
    @InjectModel(TeamNews.name)
    private readonly newsModel: Model<TeamNewsDocument>,
    @InjectModel(User.name) private readonly userModel: Model<User>,
    private readonly teams: TeamsService,
    private readonly attachments: TeamNewsAttachmentsService,
  ) {}

  private assertCanPublish(team: PlayerTeamDocument, userId: string): void {
    const role = this.teams.getSquadRoleForUser(team, userId);
    if (
      role !== PlayerTeamMemberRole.R4 &&
      role !== PlayerTeamMemberRole.R5
    ) {
      throw new ForbiddenException(
        'Only squad roles R4 and R5 can publish team news',
      );
    }
  }

  async assertMayUploadNewsImage(teamId: string, userId: string): Promise<void> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    this.assertCanPublish(team, userId);
  }

  private assertCanEditOrDelete(
    team: PlayerTeamDocument,
    news: { authorUserId: string },
    userId: string,
  ): void {
    const role = this.teams.getSquadRoleForUser(team, userId);
    if (news.authorUserId === userId) {
      return;
    }
    if (role === PlayerTeamMemberRole.R5) {
      return;
    }
    throw new ForbiddenException('Not allowed to modify this news post');
  }

  private buildPollFromInput(
    poll: CreateTeamNewsDto['poll'],
  ): TeamNews['poll'] | null {
    if (!poll) return null;
    return {
      question: poll.question.trim(),
      options: poll.optionTexts.map((text) => ({
        id: randomUUID(),
        text: text.trim(),
      })),
      votes: [],
    };
  }

  private pollTallies(
    poll: NonNullable<TeamNews['poll']>,
  ): Array<{ optionId: string; count: number }> {
    const counts = new Map<string, number>();
    for (const o of poll.options) {
      counts.set(o.id, 0);
    }
    for (const v of poll.votes) {
      counts.set(v.optionId, (counts.get(v.optionId) ?? 0) + 1);
    }
    return poll.options.map((o) => ({
      optionId: o.id,
      count: counts.get(o.id) ?? 0,
    }));
  }

  private myVote(poll: NonNullable<TeamNews['poll']>, userId: string): string | null {
    const v = poll.votes.find((x) => x.userId === userId);
    return v?.optionId ?? null;
  }

  private async usernamesFor(
    ids: string[],
  ): Promise<Map<string, string>> {
    const unique = [...new Set(ids)].filter((id) => Types.ObjectId.isValid(id));
    if (unique.length === 0) return new Map();
    const users = await this.userModel
      .find({ _id: { $in: unique.map((i) => new Types.ObjectId(i)) } })
      .select('username')
      .lean<Array<{ _id: Types.ObjectId; username: string }>>()
      .exec();
    return new Map(users.map((u) => [u._id.toString(), u.username]));
  }

  private toListRow(
    doc: TeamNews,
    nameById: Map<string, string>,
    viewerUserId: string,
  ): TeamNewsListRow {
    const poll = doc.poll;
    const tallies = poll ? this.pollTallies(poll) : [];
    const myVoteOptionId = poll ? this.myVote(poll, viewerUserId) : null;
    const first =
      doc.imageAttachments.length > 0
        ? `/teams/${doc.teamId.toString()}/news/attachments/${doc.imageAttachments[0].fileId.toString()}`
        : null;
    const excerpt =
      doc.body.length > 220 ? `${doc.body.slice(0, 217).trimEnd()}…` : doc.body;
    const created = (doc as unknown as { createdAt?: Date }).createdAt;
    const updated = (doc as unknown as { updatedAt?: Date }).updatedAt;
    return {
      id: (doc as unknown as { _id: Types.ObjectId })._id.toString(),
      teamId: doc.teamId.toString(),
      title: doc.title,
      excerpt,
      authorUserId: doc.authorUserId,
      authorUsername: nameById.get(doc.authorUserId) ?? '?',
      createdAt: created?.toISOString() ?? new Date(0).toISOString(),
      updatedAt: updated?.toISOString() ?? new Date(0).toISOString(),
      hasPoll: !!poll,
      firstImageRelativeUrl: first,
      pollTallies: tallies,
      myVoteOptionId,
    };
  }

  async list(
    teamId: string,
    userId: string,
    cursor?: string,
    limit = 20,
  ): Promise<{ items: TeamNewsListRow[]; nextCursor: string | null }> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const lim = Math.min(50, Math.max(1, limit));
    const filter: Record<string, unknown> = {
      teamId: new Types.ObjectId(teamId),
    };
    if (cursor && Types.ObjectId.isValid(cursor)) {
      filter._id = { $lt: new Types.ObjectId(cursor) };
    }
    const rows = await this.newsModel
      .find(filter)
      .sort({ _id: -1 })
      .limit(lim + 1)
      .lean<
        Array<
          TeamNews & { _id: Types.ObjectId; createdAt: Date; updatedAt: Date }
        >
      >()
      .exec();
    const hasMore = rows.length > lim;
    const page = hasMore ? rows.slice(0, lim) : rows;
    const authorIds = page.map((r) => r.authorUserId);
    const names = await this.usernamesFor(authorIds);
    const items = page.map((r) => this.toListRow(r, names, userId));
    const nextCursor =
      hasMore && page.length > 0
        ? page[page.length - 1]._id.toString()
        : null;
    return { items, nextCursor };
  }

  async getOne(
    teamId: string,
    newsId: string,
    userId: string,
  ): Promise<TeamNewsDetail> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(newsId)) {
      throw new NotFoundException('News not found');
    }
    const doc = await this.newsModel
      .findOne({
        _id: new Types.ObjectId(newsId),
        teamId: new Types.ObjectId(teamId),
      })
      .lean<
        (TeamNews & {
          _id: Types.ObjectId;
          createdAt: Date;
          updatedAt: Date;
        }) | null
      >()
      .exec();
    if (!doc) throw new NotFoundException('News not found');
    const names = await this.usernamesFor([doc.authorUserId]);
    const base = this.toListRow(doc, names, userId);
    const images = doc.imageAttachments.map(
      (a) =>
        `/teams/${doc.teamId.toString()}/news/attachments/${a.fileId.toString()}`,
    );
    const pollOut = doc.poll
      ? {
          question: doc.poll.question,
          options: doc.poll.options,
          votes: doc.poll.votes,
          tallies: this.pollTallies(doc.poll),
          myVoteOptionId: this.myVote(doc.poll, userId),
        }
      : null;
    return {
      ...base,
      body: doc.body,
      imageRelativeUrls: images,
      poll: pollOut,
    };
  }

  async create(
    teamId: string,
    userId: string,
    dto: CreateTeamNewsDto,
  ): Promise<TeamNewsDetail> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    this.assertCanPublish(team, userId);
    const teamOid = new Types.ObjectId(teamId);
    const imgs = await this.attachments.assertTeamImageSlots(
      teamOid,
      dto.imageFileIds ?? [],
      userId,
    );
    const poll = this.buildPollFromInput(dto.poll);
    const created = await this.newsModel.create({
      teamId: teamOid,
      authorUserId: userId,
      title: dto.title.trim(),
      body: dto.body.trim(),
      imageAttachments: imgs,
      poll,
    });
    return this.getOne(teamId, created._id.toString(), userId);
  }

  async update(
    teamId: string,
    newsId: string,
    userId: string,
    dto: UpdateTeamNewsDto,
  ): Promise<TeamNewsDetail> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(newsId)) {
      throw new NotFoundException('News not found');
    }
    const doc = await this.newsModel
      .findOne({
        _id: new Types.ObjectId(newsId),
        teamId: new Types.ObjectId(teamId),
      })
      .exec();
    if (!doc) throw new NotFoundException('News not found');
    this.assertCanEditOrDelete(team, doc, userId);
    if (dto.title != null) doc.title = dto.title.trim();
    if (dto.body != null) doc.body = dto.body.trim();
    if (dto.clearPoll === true) {
      doc.poll = null;
    } else if (dto.poll) {
      doc.poll = this.buildPollFromInput(dto.poll);
    }
    if (dto.imageFileIds != null) {
      const teamOid = new Types.ObjectId(teamId);
      const oldIds = doc.imageAttachments.map((x) => x.fileId);
      const incoming = await this.attachments.assertTeamImageSlots(
        teamOid,
        dto.imageFileIds,
        userId,
      );
      const newIds = incoming.map((i) => i.fileId);
      const removed = oldIds.filter(
        (oid) => !newIds.some((n) => n.equals(oid)),
      );
      if (removed.length > 0) {
        await this.attachments.deleteByIds(removed);
      }
      doc.imageAttachments = incoming;
    }
    await doc.save();
    return this.getOne(teamId, newsId, userId);
  }

  async delete(teamId: string, newsId: string, userId: string): Promise<void> {
    const team = await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(newsId)) {
      throw new NotFoundException('News not found');
    }
    const doc = await this.newsModel
      .findOne({
        _id: new Types.ObjectId(newsId),
        teamId: new Types.ObjectId(teamId),
      })
      .exec();
    if (!doc) throw new NotFoundException('News not found');
    this.assertCanEditOrDelete(team, doc, userId);
    const fileIds = doc.imageAttachments.map((x) => x.fileId);
    await doc.deleteOne();
    if (fileIds.length > 0) {
      await this.attachments.deleteByIds(fileIds);
    }
  }

  async vote(
    teamId: string,
    newsId: string,
    userId: string,
    optionId: string,
  ): Promise<TeamNewsDetail> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    if (!Types.ObjectId.isValid(newsId)) {
      throw new NotFoundException('News not found');
    }
    const doc = await this.newsModel
      .findOne({
        _id: new Types.ObjectId(newsId),
        teamId: new Types.ObjectId(teamId),
      })
      .exec();
    if (!doc) throw new NotFoundException('News not found');
    if (!doc.poll) {
      throw new ForbiddenException('This news post has no poll');
    }
    const valid = doc.poll.options.some((o) => o.id === optionId);
    if (!valid) {
      throw new BadRequestException('Invalid poll option');
    }
    doc.poll.votes = doc.poll.votes.filter((v) => v.userId !== userId);
    doc.poll.votes.push({ userId, optionId });
    await doc.save();
    return this.getOne(teamId, newsId, userId);
  }
}
