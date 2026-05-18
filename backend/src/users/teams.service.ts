import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Inject,
  Injectable,
  NotFoundException,
  forwardRef,
} from '@nestjs/common';
import { ChatRoomsService } from '../chat/chat-rooms.service';
import { playerTeamChatAllianceId } from '../chat/chat-alliance-scope';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import {
  PlayerTeamMemberRole,
  SQUAD_ROLES_ASSIGNABLE_BY_LEADER,
} from '../common/enums/player-team-member-role.enum';
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
  allianceRole: string;
  teamRole: string;
  telegramUsername: string | null;
  /** ingame | online | away — для клиента «в игре / нет». */
  presenceStatus: string | null;
  /** ISO time последнего пинга присутствия (в т.ч. оверлей в игре). */
  lastPresenceAt: string | null;
};

export type PlayerTeamAdminRow = {
  id: string;
  tag: string;
  displayName: string;
  leaderUserId: string;
  leaderUsername: string;
  memberCount: number;
  /** Distinct chat routing keys (allianceName) among members. */
  chatRoutingSummary: string;
};

export type AdminTeamMemberRow = TeamMemberRow & {
  email: string;
  membershipStatus: string;
  allianceName: string;
};

export type TeamJoinRequestRow = {
  id: string;
  requesterUserId: string;
  requesterUsername: string;
  createdAt: string;
};

function squadRoleRank(role: string): number {
  switch (role) {
    case PlayerTeamMemberRole.R5:
      return 5;
    case PlayerTeamMemberRole.R4:
      return 4;
    case PlayerTeamMemberRole.R3:
      return 3;
    case PlayerTeamMemberRole.R2:
      return 2;
    case PlayerTeamMemberRole.R1:
      return 1;
    default:
      return 0;
  }
}

@Injectable()
export class TeamsService {
  constructor(
    @InjectModel(PlayerTeam.name)
    private readonly teamModel: Model<PlayerTeamDocument>,
    @InjectModel(TeamJoinRequest.name)
    private readonly joinRequestModel: Model<TeamJoinRequestDocument>,
    @InjectModel(User.name) private readonly userModel: Model<User>,
    @Inject(forwardRef(() => ChatRoomsService))
    private readonly chatRoomsService: ChatRoomsService,
  ) {}

  private async ensurePlayerTeamChatRooms(
    team: PlayerTeamDocument,
  ): Promise<void> {
    await this.chatRoomsService.ensureAllianceChatRoomsForScope(
      playerTeamChatAllianceId(team._id.toString()),
      team.displayName,
    );
  }

  private async applyPlayerTeamMembershipToUser(
    team: PlayerTeamDocument,
    userId: Types.ObjectId,
    squadRole: PlayerTeamMemberRole,
  ): Promise<void> {
    const leader = await this.userModel.findById(team.leaderUserId).exec();
    await this.ensurePlayerTeamChatRooms(team);
    await this.userModel
      .updateOne(
        { _id: userId },
        {
          $set: {
            playerTeamId: team._id,
            teamDisplayName: team.displayName,
            teamTag: team.tag,
            ...(leader
              ? { allianceName: leader.allianceName }
              : {}),
          },
        },
      )
      .exec();
    void squadRole;
  }

  private normalizeTag(raw: string): string {
    const chars = [...raw.trim()];
    if (chars.length < 3 || chars.length > 4) {
      throw new BadRequestException('Team tag must be 3-4 letters');
    }
    for (const c of chars) {
      if (!/\p{L}/u.test(c)) {
        throw new BadRequestException('Team tag must contain only letters');
      }
    }
    return chars.map((c) => c.toLocaleUpperCase('ru-RU')).join('');
  }

  private escapeRegex(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  private async findTeamByDisplayNameCaseInsensitive(
    displayName: string,
    excludeId?: Types.ObjectId,
  ): Promise<PlayerTeamDocument | null> {
    const esc = this.escapeRegex(displayName.trim());
    const filter: Record<string, unknown> = {
      displayName: new RegExp(`^${esc}$`, 'i'),
    };
    if (excludeId) {
      filter._id = { $ne: excludeId };
    }
    return this.teamModel.findOne(filter).exec();
  }

  private async findTeamByTag(
    tag: string,
    excludeId?: Types.ObjectId,
  ): Promise<PlayerTeamDocument | null> {
    const filter: Record<string, unknown> = { tag };
    if (excludeId) {
      filter._id = { $ne: excludeId };
    }
    return this.teamModel.findOne(filter).exec();
  }

  /**
   * Ensures `squadMembers` is populated (migrates legacy `memberUserIds` if needed).
   * Mutates the passed `team` document in memory when migration runs.
   */
  private async migrateLegacyIfNeeded(team: PlayerTeamDocument): Promise<void> {
    const hasSquad =
      Array.isArray(team.squadMembers) && team.squadMembers.length > 0;
    const legacy =
      Array.isArray(team.memberUserIds) && team.memberUserIds.length > 0;

    if (hasSquad && legacy) {
      await this.teamModel
        .updateOne({ _id: team._id }, { $unset: { memberUserIds: 1 } })
        .exec();
      team.memberUserIds = undefined;
      return;
    }

    if (hasSquad && !legacy) {
      return;
    }

    if (legacy) {
      const squadMembers = team.memberUserIds!.map((userId) => ({
        userId,
        role: userId.equals(team.leaderUserId)
          ? PlayerTeamMemberRole.R5
          : PlayerTeamMemberRole.R1,
      }));
      await this.teamModel
        .updateOne(
          { _id: team._id },
          { $set: { squadMembers }, $unset: { memberUserIds: 1 } },
        )
        .exec();
      team.squadMembers = squadMembers as PlayerTeamDocument['squadMembers'];
      team.memberUserIds = undefined;
      return;
    }

    // No squad list and no legacy: repair with leader only
    const leaderOid = team.leaderUserId;
    const squadMembers = [
      { userId: leaderOid, role: PlayerTeamMemberRole.R5 },
    ];
    await this.teamModel
      .updateOne({ _id: team._id }, { $set: { squadMembers } })
      .exec();
    team.squadMembers = squadMembers as PlayerTeamDocument['squadMembers'];
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
    const team = await this.teamModel.findById(user.playerTeamId).exec();
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
    await this.migrateLegacyIfNeeded(team);
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
    const ok = team.squadMembers.some((m) => m.userId.equals(uid));
    if (!ok) {
      throw new ForbiddenException('Not a member of this team');
    }
  }

  /** Для сервисов (новости команды и т.п.), которым нужен документ команды после проверки членства. */
  async getTeamIfMemberOrThrow(
    teamId: string,
    userId: string,
  ): Promise<PlayerTeamDocument> {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    await this.migrateLegacyIfNeeded(team);
    this.assertMember(team, userId);
    return team;
  }

  getSquadRoleForUser(
    team: PlayerTeamDocument,
    userId: string,
  ): PlayerTeamMemberRole | null {
    const uid = new Types.ObjectId(userId);
    const m = team.squadMembers.find((x) => x.userId.equals(uid));
    return m?.role ?? null;
  }

  /** Squad rank for chat/UI (not alliance app role on User.role). */
  async resolveSquadRolesByUserIds(
    userIds: string[],
  ): Promise<Map<string, PlayerTeamMemberRole>> {
    const out = new Map<string, PlayerTeamMemberRole>();
    const unique = [...new Set(userIds.filter(Boolean))];
    if (unique.length === 0) {
      return out;
    }

    const validOids = unique
      .filter((id) => Types.ObjectId.isValid(id))
      .map((id) => new Types.ObjectId(id));
    const users =
      validOids.length === 0
        ? []
        : await this.userModel
            .find({ _id: { $in: validOids } })
            .select('_id playerTeamId')
            .lean<
              Array<{
                _id: Types.ObjectId;
                playerTeamId?: Types.ObjectId | null;
              }>
            >()
            .exec();

    const teamIdStrs = [
      ...new Set(
        users
          .map((u) => u.playerTeamId?.toString())
          .filter((v): v is string => Boolean(v)),
      ),
    ];
    const teams =
      teamIdStrs.length === 0
        ? []
        : await this.teamModel
            .find({
              _id: {
                $in: teamIdStrs.map((id) => new Types.ObjectId(id)),
              },
            })
            .exec();
    const teamById = new Map(teams.map((t) => [t._id.toString(), t]));

    for (const id of unique) {
      if (!Types.ObjectId.isValid(id)) {
        out.set(id, PlayerTeamMemberRole.R1);
        continue;
      }
      const user = users.find((u) => u._id.toString() === id);
      const teamId = user?.playerTeamId?.toString();
      if (!user || !teamId) {
        out.set(id, PlayerTeamMemberRole.R1);
        continue;
      }
      const team = teamById.get(teamId);
      if (!team) {
        out.set(id, PlayerTeamMemberRole.R1);
        continue;
      }
      await this.migrateLegacyIfNeeded(team);
      const leaderStr = team.leaderUserId.toString();
      out.set(
        id,
        this.getSquadRoleForUser(team, id) ??
          (id === leaderStr
            ? PlayerTeamMemberRole.R5
            : PlayerTeamMemberRole.R1),
      );
    }
    return out;
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
    const nameTaken = await this.findTeamByDisplayNameCaseInsensitive(nameTrim);
    if (nameTaken) {
      throw new ConflictException('This team name is already taken');
    }
    const tagTaken = await this.findTeamByTag(tag);
    if (tagTaken) {
      throw new ConflictException('This team tag is already taken');
    }
    const leaderOid = new Types.ObjectId(userId);
    let team: PlayerTeamDocument;
    try {
      team = await this.teamModel.create({
        leaderUserId: leaderOid,
        tag,
        displayName: nameTrim.slice(0, 48),
        squadMembers: [
          { userId: leaderOid, role: PlayerTeamMemberRole.R5 },
        ],
      });
    } catch (e: unknown) {
      const code = (e as { code?: number })?.code;
      if (code === 11000) {
        throw new ConflictException('This team tag is already taken');
      }
      throw e;
    }
    await this.ensurePlayerTeamChatRooms(team);
    await this.userModel
      .updateOne(
        { _id: userId },
        {
          $set: {
            playerTeamId: team._id,
            teamDisplayName: team.displayName,
            teamTag: team.tag,
          },
        },
      )
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
    await this.migrateLegacyIfNeeded(team);
    this.assertMember(team, requesterUserId);

    const ids = team.squadMembers.map((m) => m.userId.toString());
    const roleByUserId = new Map(
      team.squadMembers.map((m) => [m.userId.toString(), m.role]),
    );
    const users = await this.userModel
      .find({ _id: { $in: team.squadMembers.map((m) => m.userId) } })
      .select('username role telegramUsername presenceStatus lastPresenceAt')
      .lean<
        Array<{
          _id: Types.ObjectId;
          username: string;
          role: string;
          telegramUsername?: string | null;
          presenceStatus?: string | null;
          lastPresenceAt?: Date | string | null;
        }>
      >()
      .exec();
    const toIso = (v: Date | string | null | undefined): string | null => {
      if (v == null) return null;
      if (v instanceof Date) return v.toISOString();
      if (typeof v === 'string' && v.trim().length > 0) return v.trim();
      return null;
    };
    const byId = new Map(
      users.map((u) => [
        u._id.toString(),
        {
          username: u.username,
          role: u.role,
          telegramUsername: u.telegramUsername ?? null,
          presenceStatus: u.presenceStatus ?? null,
          lastPresenceAt: toIso(u.lastPresenceAt),
        },
      ]),
    );
    const leaderStr = team.leaderUserId.toString();
    const members: TeamMemberRow[] = ids.map((id) => {
      const teamRole =
        roleByUserId.get(id) ??
        (id === leaderStr ? PlayerTeamMemberRole.R5 : PlayerTeamMemberRole.R1);
      const row = byId.get(id);
      return {
        userId: id,
        username: row?.username ?? '?',
        isLeader: id === leaderStr,
        allianceRole: row?.role ?? 'R2',
        teamRole,
        telegramUsername: row?.telegramUsername ?? null,
        presenceStatus: row?.presenceStatus ?? null,
        lastPresenceAt: row?.lastPresenceAt ?? null,
      };
    });
    members.sort((a, b) => {
      const dr = squadRoleRank(b.teamRole) - squadRoleRank(a.teamRole);
      if (dr !== 0) {
        return dr;
      }
      return a.username.localeCompare(b.username, 'ru', {
        sensitivity: 'base',
      });
    });
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
    await this.migrateLegacyIfNeeded(team);
    const requester = await this.userModel.findById(requesterUserId).exec();
    if (!requester) {
      throw new NotFoundException('User not found');
    }
    if (requester.playerTeamId) {
      throw new ConflictException('You already belong to a team');
    }
    const rid = new Types.ObjectId(requesterUserId);
    if (team.squadMembers.some((m) => m.userId.equals(rid))) {
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
    await this.migrateLegacyIfNeeded(team);
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
    if (team.squadMembers.some((m) => m.userId.equals(rid))) {
      reqDoc.status = TeamJoinRequestStatus.ACCEPTED;
      await reqDoc.save();
      return { ok: true };
    }
    await this.teamModel
      .updateOne(
        { _id: team._id },
        {
          $push: {
            squadMembers: {
              userId: rid,
              role: PlayerTeamMemberRole.R1,
            },
          },
        },
      )
      .exec();
    await this.applyPlayerTeamMembershipToUser(
      team,
      rid,
      PlayerTeamMemberRole.R1,
    );
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
    if (team.squadMembers.some((m) => m.userId.equals(tid))) {
      throw new ConflictException('Already a member');
    }
    await this.teamModel
      .updateOne(
        { _id: team._id },
        {
          $push: {
            squadMembers: {
              userId: tid,
              role: PlayerTeamMemberRole.R1,
            },
          },
        },
      )
      .exec();
    await this.applyPlayerTeamMembershipToUser(
      team,
      tid,
      PlayerTeamMemberRole.R1,
    );
    return { ok: true };
  }

  async removeMember(
    teamId: string,
    leaderUserId: string,
    memberUserId: string,
  ) {
    const team = await this.assertTeamLeader(teamId, leaderUserId);
    if (team.leaderUserId.toString() === memberUserId) {
      throw new BadRequestException('Cannot remove the team leader');
    }
    const mid = new Types.ObjectId(memberUserId);
    if (!team.squadMembers.some((m) => m.userId.equals(mid))) {
      throw new NotFoundException('Member not on this team');
    }
    await this.teamModel
      .updateOne({ _id: team._id }, { $pull: { squadMembers: { userId: mid } } })
      .exec();
    await this.userModel
      .updateOne(
        { _id: mid },
        {
          $set: {
            playerTeamId: null,
            teamDisplayName: null,
            teamTag: null,
          },
        },
      )
      .exec();
    return { ok: true };
  }

  async updateTeamBranding(
    teamId: string,
    leaderUserId: string,
    rawName: string,
    rawTag: string,
  ): Promise<{ ok: true }> {
    const team = await this.assertTeamLeader(teamId, leaderUserId);
    const nameTrim = rawName.trim();
    if (nameTrim.length < 2) {
      throw new BadRequestException('Team name is too short');
    }
    const nameVal = nameTrim.slice(0, 48);
    const tagNorm = this.normalizeTag(rawTag);

    const nameOther = await this.findTeamByDisplayNameCaseInsensitive(
      nameVal,
      team._id as Types.ObjectId,
    );
    if (nameOther) {
      throw new ConflictException('This team name is already taken');
    }
    const tagOther = await this.findTeamByTag(tagNorm, team._id as Types.ObjectId);
    if (tagOther) {
      throw new ConflictException('This team tag is already taken');
    }

    await this.teamModel
      .updateOne(
        { _id: team._id },
        { $set: { displayName: nameVal, tag: tagNorm } },
      )
      .exec();
    await this.userModel
      .updateMany(
        { playerTeamId: team._id },
        { $set: { teamDisplayName: nameVal, teamTag: tagNorm } },
      )
      .exec();
    return { ok: true };
  }

  async updateMemberSquadRole(
    teamId: string,
    leaderUserId: string,
    targetUserId: string,
    rawRole: string,
  ): Promise<{ ok: true }> {
    const team = await this.assertTeamLeader(teamId, leaderUserId);
    if (team.leaderUserId.toString() === targetUserId) {
      throw new BadRequestException('Cannot change the leader squad role');
    }
    const roleNorm = rawRole.trim().toUpperCase();
    if (
      !SQUAD_ROLES_ASSIGNABLE_BY_LEADER.includes(
        roleNorm as PlayerTeamMemberRole,
      )
    ) {
      throw new BadRequestException('Invalid squad role');
    }
    const tid = new Types.ObjectId(targetUserId);
    if (!team.squadMembers.some((m) => m.userId.equals(tid))) {
      throw new NotFoundException('Member not on this team');
    }
    const res = await this.teamModel
      .updateOne(
        { _id: team._id },
        { $set: { 'squadMembers.$[m].role': roleNorm } },
        {
          arrayFilters: [{ 'm.userId': tid }],
        },
      )
      .exec();
    if (res.matchedCount !== 1) {
      throw new NotFoundException('Member not on this team');
    }
    return { ok: true };
  }

  async countTeams(): Promise<number> {
    return this.teamModel.countDocuments({}).exec();
  }

  async listAllTeamsForAdmin(): Promise<PlayerTeamAdminRow[]> {
    const teams = await this.teamModel
      .find({})
      .sort({ displayName: 1, tag: 1 })
      .exec();
    const out: PlayerTeamAdminRow[] = [];
    for (const team of teams) {
      await this.migrateLegacyIfNeeded(team);
      const leader = await this.userModel
        .findById(team.leaderUserId)
        .select('username')
        .lean<{ username?: string }>()
        .exec();
      const memberUsers = await this.userModel
        .find({ playerTeamId: team._id })
        .select('allianceName')
        .lean<Array<{ allianceName?: string }>>()
        .exec();
      const routes = [
        ...new Set(
          memberUsers
            .map((u) => u.allianceName?.trim())
            .filter((v): v is string => Boolean(v)),
        ),
      ];
      out.push({
        id: team._id.toString(),
        tag: team.tag,
        displayName: team.displayName,
        leaderUserId: team.leaderUserId.toString(),
        leaderUsername: leader?.username?.trim() || '—',
        memberCount: team.squadMembers.length,
        chatRoutingSummary: routes.length > 0 ? routes.join(', ') : '—',
      });
    }
    return out;
  }

  async getTeamDetailForAdmin(teamId: string): Promise<{
    id: string;
    tag: string;
    displayName: string;
    leaderUserId: string;
    members: AdminTeamMemberRow[];
  } | null> {
    if (!Types.ObjectId.isValid(teamId)) {
      return null;
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      return null;
    }
    await this.migrateLegacyIfNeeded(team);
    const users = await this.userModel
      .find({ _id: { $in: team.squadMembers.map((m) => m.userId) } })
      .select(
        'username email role telegramUsername presenceStatus lastPresenceAt membershipStatus allianceName',
      )
      .lean<
        Array<{
          _id: Types.ObjectId;
          username: string;
          email: string;
          role: string;
          telegramUsername?: string | null;
          presenceStatus?: string | null;
          lastPresenceAt?: Date | string | null;
          membershipStatus?: string;
          allianceName?: string;
        }>
      >()
      .exec();
    const roleByUserId = new Map(
      team.squadMembers.map((m) => [m.userId.toString(), m.role]),
    );
    const toIso = (v: Date | string | null | undefined): string | null => {
      if (v == null) return null;
      if (v instanceof Date) return v.toISOString();
      return String(v);
    };
    const members: AdminTeamMemberRow[] = users.map((u) => {
      const uid = u._id.toString();
      return {
        userId: uid,
        username: u.username,
        email: u.email,
        isLeader: team.leaderUserId.equals(u._id),
        allianceRole: u.role,
        teamRole: roleByUserId.get(uid) ?? 'R1',
        telegramUsername: u.telegramUsername ?? null,
        presenceStatus: u.presenceStatus ?? null,
        lastPresenceAt: toIso(u.lastPresenceAt),
        membershipStatus: u.membershipStatus ?? 'active',
        allianceName: u.allianceName?.trim() || '—',
      };
    });
    members.sort((a, b) => squadRoleRank(b.teamRole) - squadRoleRank(a.teamRole));
    return {
      id: team._id.toString(),
      tag: team.tag,
      displayName: team.displayName,
      leaderUserId: team.leaderUserId.toString(),
      members,
    };
  }
}
