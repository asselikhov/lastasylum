import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { User, UserDocument } from './schemas/user.schema';
import { PlayerTeam, PlayerTeamDocument } from './schemas/player-team.schema';
import {
  TeamJoinRequest,
  TeamJoinRequestDocument,
  TeamJoinRequestStatus,
} from './schemas/team-join-request.schema';

export type PlayerTeamProfileFields = {
  playerTeamId: string | null;
  playerTeamTag: string | null;
  playerTeamDisplayName: string | null;
  playerTeamLeaderUserId: string | null;
  isPlayerTeamLeader: boolean;
  pendingPlayerTeamJoinRequests: number;
};

export type TeamMemberRow = {
  userId: string;
  username: string;
  isLeader: boolean;
};

export type TeamJoinRequestRow = {
  id: string;
  requesterUserId: string;
  requesterUsername: string;
  createdAt: string;
};

@Injectable()
export class TeamsService {
  constructor(
    @InjectModel(PlayerTeam.name)
    private readonly teamModel: Model<PlayerTeamDocument>,
    @InjectModel(TeamJoinRequest.name)
    private readonly joinRequestModel: Model<TeamJoinRequestDocument>,
    @InjectModel(User.name) private readonly userModel: Model<User>,
  ) {}

  private normalizeTag(raw: string): string {
    const chars = [...raw.trim()];
    if (chars.length !== 3) {
      throw new BadRequestException('Team tag must be exactly 3 letters');
    }
    for (const c of chars) {
      if (!/\p{L}/u.test(c)) {
        throw new BadRequestException('Team tag must contain only letters');
      }
    }
    return chars.map((c) => c.toLocaleUpperCase('ru-RU')).join('');
  }

  async getPlayerTeamProfileFields(
    user: UserDocument,
  ): Promise<PlayerTeamProfileFields> {
    if (!user.playerTeamId) {
      return {
        playerTeamId: null,
        playerTeamTag: null,
        playerTeamDisplayName: null,
        playerTeamLeaderUserId: null,
        isPlayerTeamLeader: false,
        pendingPlayerTeamJoinRequests: 0,
      };
    }
    const team = await this.teamModel.findById(user.playerTeamId).lean().exec();
    if (!team) {
      return {
        playerTeamId: null,
        playerTeamTag: null,
        playerTeamDisplayName: null,
        playerTeamLeaderUserId: null,
        isPlayerTeamLeader: false,
        pendingPlayerTeamJoinRequests: 0,
      };
    }
    const leaderId = team.leaderUserId.toString();
    const uid = user._id.toString();
    const isLeader = leaderId === uid;
    let pending = 0;
    if (isLeader) {
      pending = await this.joinRequestModel.countDocuments({
        teamId: team._id,
        status: TeamJoinRequestStatus.PENDING,
      });
    }
    return {
      playerTeamId: team._id.toString(),
      playerTeamTag: team.tag,
      playerTeamDisplayName: team.displayName,
      playerTeamLeaderUserId: leaderId,
      isPlayerTeamLeader: isLeader,
      pendingPlayerTeamJoinRequests: pending,
    };
  }

  private assertMember(team: PlayerTeamDocument, userId: string): void {
    const uid = new Types.ObjectId(userId);
    const ok = team.memberUserIds.some((id) => id.equals(uid));
    if (!ok) {
      throw new ForbiddenException('Not a member of this team');
    }
  }

  async createTeam(
    userId: string,
    displayName: string,
    rawTag: string,
  ): Promise<{ teamId: string }> {
    const user = await this.userModel.findById(userId).exec();
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if (user.playerTeamId) {
      throw new ConflictException('You already belong to a team');
    }
    const tag = this.normalizeTag(rawTag);
    const nameTrim = displayName.trim();
    if (nameTrim.length < 2) {
      throw new BadRequestException('Team name is too short');
    }
    const leaderOid = new Types.ObjectId(userId);
    let team: PlayerTeamDocument;
    try {
      team = await this.teamModel.create({
        leaderUserId: leaderOid,
        tag,
        displayName: nameTrim.slice(0, 48),
        memberUserIds: [leaderOid],
      });
    } catch (e: unknown) {
      const code = (e as { code?: number })?.code;
      if (code === 11000) {
        throw new ConflictException('This team tag is already taken');
      }
      throw e;
    }
    await this.userModel
      .updateOne({ _id: userId }, { $set: { playerTeamId: team._id } })
      .exec();
    return { teamId: team._id.toString() };
  }

  async getTeamDetailForUser(
    teamId: string,
    requesterUserId: string,
  ): Promise<{
    id: string;
    tag: string;
    displayName: string;
    leaderUserId: string;
    members: TeamMemberRow[];
  }> {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    this.assertMember(team, requesterUserId);
    const ids = team.memberUserIds.map((id) => id.toString());
    const users = await this.userModel
      .find({ _id: { $in: team.memberUserIds } })
      .select('username')
      .lean<Array<{ _id: Types.ObjectId; username: string }>>()
      .exec();
    const byId = new Map(users.map((u) => [u._id.toString(), u.username]));
    const leaderStr = team.leaderUserId.toString();
    const members: TeamMemberRow[] = ids.map((id) => ({
      userId: id,
      username: byId.get(id) ?? '?',
      isLeader: id === leaderStr,
    }));
    return {
      id: team._id.toString(),
      tag: team.tag,
      displayName: team.displayName,
      leaderUserId: leaderStr,
      members,
    };
  }

  async searchTeams(q: string, limit = 20) {
    const term = q?.trim() ?? '';
    if (term.length < 1) {
      return [];
    }
    const esc = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const rx = new RegExp(esc, 'i');
    const rows = await this.teamModel
      .find({
        $or: [{ tag: rx }, { displayName: rx }],
      })
      .sort({ tag: 1 })
      .limit(Math.min(50, Math.max(1, limit)))
      .lean<Array<{ _id: Types.ObjectId; tag: string; displayName: string }>>()
      .exec();
    return rows.map((r) => ({
      id: r._id.toString(),
      tag: r.tag,
      displayName: r.displayName,
    }));
  }

  async submitJoinRequest(teamId: string, requesterUserId: string) {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    const requester = await this.userModel.findById(requesterUserId).exec();
    if (!requester) {
      throw new NotFoundException('User not found');
    }
    if (requester.playerTeamId) {
      throw new ConflictException('You already belong to a team');
    }
    const rid = new Types.ObjectId(requesterUserId);
    if (team.memberUserIds.some((id) => id.equals(rid))) {
      throw new ConflictException('Already a member');
    }
    const dup = await this.joinRequestModel.findOne({
      teamId: team._id,
      requesterUserId: rid,
      status: TeamJoinRequestStatus.PENDING,
    });
    if (dup) {
      throw new ConflictException('Join request already pending');
    }
    const doc = await this.joinRequestModel.create({
      teamId: team._id,
      requesterUserId: rid,
      status: TeamJoinRequestStatus.PENDING,
    });
    return { id: doc._id.toString() };
  }

  async listPendingJoinRequestsForLeader(
    leaderUserId: string,
  ): Promise<TeamJoinRequestRow[]> {
    const teams = await this.teamModel
      .find({ leaderUserId: new Types.ObjectId(leaderUserId) })
      .select('_id')
      .lean<Array<{ _id: Types.ObjectId }>>()
      .exec();
    if (teams.length === 0) {
      return [];
    }
    const teamIds = teams.map((t) => t._id);
    const reqs = await this.joinRequestModel
      .find({
        teamId: { $in: teamIds },
        status: TeamJoinRequestStatus.PENDING,
      })
      .sort({ createdAt: -1 })
      .limit(100)
      .lean<
        Array<{
          _id: Types.ObjectId;
          requesterUserId: Types.ObjectId;
          createdAt?: Date;
        }>
      >()
      .exec();
    const userIds = [...new Set(reqs.map((r) => r.requesterUserId.toString()))];
    const users = await this.userModel
      .find({ _id: { $in: userIds.map((id) => new Types.ObjectId(id)) } })
      .select('username')
      .lean<Array<{ _id: Types.ObjectId; username: string }>>()
      .exec();
    const uname = new Map(users.map((u) => [u._id.toString(), u.username]));
    return reqs.map((r) => ({
      id: r._id.toString(),
      requesterUserId: r.requesterUserId.toString(),
      requesterUsername: uname.get(r.requesterUserId.toString()) ?? '?',
      createdAt: (r.createdAt ?? new Date()).toISOString(),
    }));
  }

  private async assertTeamLeader(
    teamId: string,
    leaderUserId: string,
  ): Promise<PlayerTeamDocument> {
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    if (!team.leaderUserId.equals(new Types.ObjectId(leaderUserId))) {
      throw new ForbiddenException('Only the team leader can do this');
    }
    return team;
  }

  async acceptJoinRequest(requestId: string, leaderUserId: string) {
    if (!Types.ObjectId.isValid(requestId)) {
      throw new NotFoundException('Request not found');
    }
    const reqDoc = await this.joinRequestModel.findById(requestId).exec();
    if (!reqDoc || reqDoc.status !== TeamJoinRequestStatus.PENDING) {
      throw new NotFoundException('Request not found');
    }
    const team = await this.assertTeamLeader(
      reqDoc.teamId.toString(),
      leaderUserId,
    );
    const requesterId = reqDoc.requesterUserId.toString();
    const requester = await this.userModel.findById(requesterId).exec();
    if (!requester) {
      throw new NotFoundException('User not found');
    }
    if (requester.playerTeamId) {
      throw new ConflictException('User already joined another team');
    }
    const rid = new Types.ObjectId(requesterId);
    if (team.memberUserIds.some((id) => id.equals(rid))) {
      reqDoc.status = TeamJoinRequestStatus.ACCEPTED;
      await reqDoc.save();
      return { ok: true };
    }
    await this.teamModel
      .updateOne({ _id: team._id }, { $addToSet: { memberUserIds: rid } })
      .exec();
    await this.userModel
      .updateOne({ _id: rid }, { $set: { playerTeamId: team._id } })
      .exec();
    reqDoc.status = TeamJoinRequestStatus.ACCEPTED;
    await reqDoc.save();
    return { ok: true };
  }

  async rejectJoinRequest(requestId: string, leaderUserId: string) {
    if (!Types.ObjectId.isValid(requestId)) {
      throw new NotFoundException('Request not found');
    }
    const reqDoc = await this.joinRequestModel.findById(requestId).exec();
    if (!reqDoc || reqDoc.status !== TeamJoinRequestStatus.PENDING) {
      throw new NotFoundException('Request not found');
    }
    await this.assertTeamLeader(reqDoc.teamId.toString(), leaderUserId);
    reqDoc.status = TeamJoinRequestStatus.REJECTED;
    await reqDoc.save();
    return { ok: true };
  }

  async addMemberByUsername(
    teamId: string,
    leaderUserId: string,
    username: string,
  ) {
    const team = await this.assertTeamLeader(teamId, leaderUserId);
    const target = await this.userModel
      .findOne({ username: username.trim() })
      .exec();
    if (!target) {
      throw new NotFoundException('User not found');
    }
    if (target._id.equals(team.leaderUserId)) {
      throw new BadRequestException('Leader is already on the team');
    }
    if (target.playerTeamId) {
      throw new ConflictException('User already belongs to a team');
    }
    const tid = new Types.ObjectId(target._id.toString());
    if (team.memberUserIds.some((id) => id.equals(tid))) {
      throw new ConflictException('Already a member');
    }
    await this.teamModel
      .updateOne({ _id: team._id }, { $addToSet: { memberUserIds: tid } })
      .exec();
    await this.userModel
      .updateOne({ _id: tid }, { $set: { playerTeamId: team._id } })
      .exec();
    return { ok: true };
  }

  async removeMember(teamId: string, leaderUserId: string, memberUserId: string) {
    const team = await this.assertTeamLeader(teamId, leaderUserId);
    if (team.leaderUserId.toString() === memberUserId) {
      throw new BadRequestException('Cannot remove the team leader');
    }
    const mid = new Types.ObjectId(memberUserId);
    if (!team.memberUserIds.some((id) => id.equals(mid))) {
      throw new NotFoundException('Member not on this team');
    }
    await this.teamModel
      .updateOne({ _id: team._id }, { $pull: { memberUserIds: mid } })
      .exec();
    await this.userModel
      .updateOne({ _id: mid }, { $set: { playerTeamId: null } })
      .exec();
    return { ok: true };
  }
}
