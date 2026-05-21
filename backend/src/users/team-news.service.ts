import { randomUUID } from 'crypto';
import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import mongoose, { Model, Types } from 'mongoose';
import {
  isSquadOfficerRole,
  PlayerTeamMemberRole,
} from '../common/enums/player-team-member-role.enum';
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
  authorTelegramUsername: string | null;
  createdAt: string;
  updatedAt: string;
  hasPoll: boolean;
  pollOnly: boolean;
  pollQuestion: string | null;
  pollOptions: Array<{ id: string; text: string }> | null;
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
    votes: Array<{
      userId: string;
      username: string;
      telegramUsername: string | null;
      optionId: string;
    }>;
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
    if (isSquadOfficerRole(role)) {
      return;
    }
    throw new ForbiddenException('Not allowed to modify this news post');
  }

  private buildPollFromInput(
    poll: CreateTeamNewsDto['poll'],
    existingPoll?: TeamNews['poll'] | null,
  ): TeamNews['poll'] | null {
    if (!poll) return null;
    const question = poll.question?.trim() ?? '';
    const optionTexts = (poll.optionTexts ?? [])
      .map((text) => text.trim())
      .filter((text) => text.length > 0);
    if (!question || optionTexts.length < 2) {
      throw new BadRequestException(
        'Poll requires a question and at least two options',
      );
    }
    const rawIds = poll.optionIds ?? [];
    if (rawIds.length > 0 && rawIds.length !== optionTexts.length) {
      throw new BadRequestException(
        'Poll optionIds length must match optionTexts',
      );
    }
    const existingById = new Map(
      (existingPoll?.options ?? []).map((o) => [o.id, o] as const),
    );
    const options = optionTexts.map((text, index) => {
      const explicitId = rawIds[index]?.trim();
      if (explicitId && existingById.has(explicitId)) {
        return { id: explicitId, text };
      }
      const prevByIndex = existingPoll?.options[index];
      if (prevByIndex) {
        return { id: prevByIndex.id, text };
      }
      const prevByText = existingPoll?.options.find(
        (o) => o.text.trim() === text,
      );
      if (prevByText) {
        return { id: prevByText.id, text };
      }
      return { id: randomUUID(), text };
    });
    const validIds = new Set(options.map((o) => o.id));
    const votes =
      existingPoll?.votes.filter((v) => validIds.has(v.optionId)) ?? [];
    return {
      question,
      options,
      votes,
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

  private idString(value: unknown): string {
    if (value == null) return '';
    if (typeof value === 'string') return value;
    if (value instanceof Types.ObjectId) return value.toString();
    if (typeof value === 'object' && 'toString' in value) {
      return String((value as { toString(): string }).toString());
    }
    return String(value);
  }

  private rethrowMongooseValidation(err: unknown): never {
    if (err instanceof mongoose.Error.ValidationError) {
      const msg = Object.values(err.errors)
        .map((e) => e.message)
        .join('; ');
      throw new BadRequestException(msg || 'Invalid news data');
    }
    throw err;
  }

  private async usernamesFor(
    ids: string[],
  ): Promise<Map<string, string>> {
    const profiles = await this.userProfilesFor(ids);
    return new Map(
      [...profiles.entries()].map(([id, p]) => [id, p.username]),
    );
  }

  private async userProfilesFor(
    ids: string[],
  ): Promise<
    Map<string, { username: string; telegramUsername: string | null }>
  > {
    const unique = [...new Set(ids)].filter((id) => Types.ObjectId.isValid(id));
    if (unique.length === 0) return new Map();
    const users = await this.userModel
      .find({ _id: { $in: unique.map((i) => new Types.ObjectId(i)) } })
      .select('username telegramUsername')
      .lean<
        Array<{
          _id: Types.ObjectId;
          username: string;
          telegramUsername?: string | null;
        }>
      >()
      .exec();
    return new Map(
      users.map((u) => [
        u._id.toString(),
        {
          username: u.username,
          telegramUsername: u.telegramUsername ?? null,
        },
      ]),
    );
  }

  private toListRow(
    doc: TeamNews,
    profilesById: Map<
      string,
      { username: string; telegramUsername: string | null }
    >,
    viewerUserId: string,
  ): TeamNewsListRow {
    const poll = doc.poll;
    const tallies = poll ? this.pollTallies(poll) : [];
    const myVoteOptionId = poll ? this.myVote(poll, viewerUserId) : null;
    const teamIdStr = this.idString(doc.teamId);
    const first =
      doc.imageAttachments.length > 0
        ? `/teams/${teamIdStr}/news/attachments/${this.idString(doc.imageAttachments[0].fileId)}`
        : null;
    const bodyTrim = doc.body.trim();
    const excerpt =
      bodyTrim.length > 220
        ? `${bodyTrim.slice(0, 217).trimEnd()}…`
        : bodyTrim;
    const pollOnly =
      !!poll && bodyTrim.length === 0 && doc.imageAttachments.length === 0;
    const created = (doc as unknown as { createdAt?: Date }).createdAt;
    const updated = (doc as unknown as { updatedAt?: Date }).updatedAt;
    const authorProfile = profilesById.get(doc.authorUserId);
    return {
      id: this.idString((doc as unknown as { _id: unknown })._id),
      teamId: teamIdStr,
      title: doc.title,
      excerpt,
      authorUserId: doc.authorUserId,
      authorUsername: authorProfile?.username ?? '?',
      authorTelegramUsername: authorProfile?.telegramUsername ?? null,
      createdAt: created?.toISOString() ?? new Date(0).toISOString(),
      updatedAt: updated?.toISOString() ?? new Date(0).toISOString(),
      hasPoll: !!poll,
      pollOnly,
      pollQuestion: poll?.question ?? null,
      pollOptions: poll?.options.map((o) => ({ id: o.id, text: o.text })) ?? null,
      firstImageRelativeUrl: first,
      pollTallies: tallies,
      myVoteOptionId,
    };
  }

  async listForAdmin(
    teamId: string,
    limit = 100,
  ): Promise<{ items: TeamNewsListRow[]; nextCursor: string | null }> {
    await this.teams.assertTeamExistsForAdmin(teamId);
    const lim = Math.min(200, Math.max(1, limit));
    const rows = await this.newsModel
      .find({ teamId: new Types.ObjectId(teamId) })
      .sort({ _id: -1 })
      .limit(lim)
      .lean<
        Array<
          TeamNews & { _id: Types.ObjectId; createdAt: Date; updatedAt: Date }
        >
      >()
      .exec();
    const authorIds = rows.map((r) => r.authorUserId);
    const profiles = await this.userProfilesFor(authorIds);
    const items = rows.map((r) => this.toListRow(r, profiles, ''));
    return { items, nextCursor: null };
  }

  /**
   * Posts in [teamId] with createdAt strictly after [afterIso] (client last-seen cursor).
   */
  async countUnread(
    teamId: string,
    userId: string,
    afterIso?: string | null,
  ): Promise<number> {
    await this.teams.getTeamIfMemberOrThrow(teamId, userId);
    const filter: Record<string, unknown> = {
      teamId: new Types.ObjectId(teamId),
    };
    const trimmed = afterIso?.trim();
    if (trimmed) {
      const after = new Date(trimmed);
      if (!Number.isNaN(after.getTime())) {
        filter.createdAt = { $gt: after };
      }
    }
    return this.newsModel.countDocuments(filter).exec();
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
    const profiles = await this.userProfilesFor(authorIds);
    const items = page.map((r) => this.toListRow(r, profiles, userId));
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
    const pollVoterIds = doc.poll?.votes.map((v) => v.userId) ?? [];
    const profiles = await this.userProfilesFor([
      doc.authorUserId,
      ...pollVoterIds,
    ]);
    const base = this.toListRow(doc, profiles, userId);
    const teamIdStr = this.idString(doc.teamId);
    const images = doc.imageAttachments.map(
      (a) =>
        `/teams/${teamIdStr}/news/attachments/${this.idString(a.fileId)}`,
    );
    const pollOut = doc.poll
      ? {
          question: doc.poll.question,
          options: doc.poll.options,
          votes: doc.poll.votes.map((v) => ({
            userId: v.userId,
            username: profiles.get(v.userId)?.username ?? '—',
            telegramUsername:
              profiles.get(v.userId)?.telegramUsername ?? null,
            optionId: v.optionId,
          })),
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
    const titleRaw = (dto.title ?? '').trim();
    const bodyRaw = (dto.body ?? '').trim();
    if (!poll && (!titleRaw || !bodyRaw)) {
      throw new BadRequestException('Title and body are required without a poll');
    }
    const titleSource = poll && !titleRaw ? poll.question : titleRaw;
    if (!titleSource) {
      throw new BadRequestException('Title is required');
    }
    const title = titleSource.slice(0, 500);
    const body = bodyRaw || '';
    let created: TeamNewsDocument;
    try {
      created = await this.newsModel.create({
        teamId: teamOid,
        authorUserId: userId,
        title,
        body,
        imageAttachments: imgs,
        poll,
      });
    } catch (err) {
      this.rethrowMongooseValidation(err);
    }
    return this.getOne(teamId, this.idString(created._id), userId);
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
    if (dto.title != null) {
      const t = dto.title.trim();
      doc.title = t.length > 0 ? t : (doc.poll?.question ?? doc.title);
    }
    if (dto.body != null) doc.body = dto.body.trim() || '';
    if (dto.clearPoll === true) {
      doc.poll = null;
    } else if (dto.poll) {
      const builtPoll = this.buildPollFromInput(dto.poll, doc.poll);
      doc.poll = builtPoll;
      const bodyEmpty = doc.body.trim().length === 0;
      const noImages = doc.imageAttachments.length === 0;
      if (builtPoll && bodyEmpty && noImages) {
        doc.title = builtPoll.question;
      }
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
