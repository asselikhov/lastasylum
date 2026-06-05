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
  paginateParams,
  type PaginatedResult,
} from '../common/dto/paginated-result.dto';
import { normalizeAllianceRole } from '../common/alliance-role.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import {
  isSquadOfficerRole,
  PlayerTeamMemberRole,
  SQUAD_ROLES_ASSIGNABLE_BY_R4,
  SQUAD_ROLES_ASSIGNABLE_BY_R5,
} from '../common/enums/player-team-member-role.enum';
import { User, UserDocument } from './schemas/user.schema';
import { PlayerTeam, PlayerTeamDocument } from './schemas/player-team.schema';
import {
  TeamJoinRequest,
  TeamJoinRequestDocument,
  TeamJoinRequestStatus,
} from './schemas/team-join-request.schema';
import { GameIdentitiesService } from './game-identities.service';
import { squadMemberUserIdEquals } from './squad-member-id.util';

export type PlayerTeamProfileFields = {
  playerTeamId: string | null;
  playerTeamTag: string | null;
  playerTeamDisplayName: string | null;
  playerTeamLeaderUserId: string | null;
  isPlayerTeamLeader: boolean;
  pendingPlayerTeamJoinRequests: number;
  /** Squad rank (R1–R5) on [playerTeamId]; null when not in a team. */
  playerTeamSquadRole: string | null;
};

export type TeamMemberRow = {
  userId: string;
  username: string;
  isLeader: boolean;
  /** App account role (MEMBER…ADMIN), not squad rank R1–R5. */
  accountRole: string;
  teamRole: string;
  telegramUsername: string | null;
  /** ingame | online | away — для клиента «в игре / нет». */
  presenceStatus: string | null;
  /** ISO: последний пинг оверлея в игре (ingame). */
  lastPresenceAt: string | null;
  /** ISO: последняя активность в приложении SquadRelay. */
  lastAppActiveAt: string | null;
};

export type PlayerTeamAdminRow = {
  id: string;
  tag: string;
  displayName: string;
  leaderUserId: string;
  leaderUsername: string;
  leaderServerNumber: number | null;
  /** Distinct server numbers where the team has at least one member identity. */
  serverNumbers: number[];
  memberCount: number;
  /** Distinct chat routing keys (allianceName) among members. */
  chatRoutingSummary: string;
};

export type AdminTeamMemberRow = TeamMemberRow & {
  email: string;
  membershipStatus: string;
  allianceName: string;
  serverNumber: number | null;
  gameNickname: string;
  accountUsername: string;
  identityId: string | null;
  appVersionName: string | null;
  appVersionCode: number | null;
  appVersionReportedAt: string | null;
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
    private readonly gameIdentities: GameIdentitiesService,
  ) {}

  /** Display name for game-event push banner (FCM data). */
  async findTeamDisplayName(teamId: string): Promise<string | null> {
    if (!Types.ObjectId.isValid(teamId)) {
      return null;
    }
    const row = await this.teamModel
      .findById(teamId)
      .select('displayName')
      .lean<{ displayName?: string }>()
      .exec();
    const name = row?.displayName?.trim();
    return name && name.length > 0 ? name : null;
  }

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
    const user = await this.userModel.findById(userId).exec();
    if (user) {
      await this.gameIdentities.ensureMigrated(user);
    }
    await this.gameIdentities.bindAllIdentitiesToTeam(
      userId.toString(),
      team._id,
      team.tag,
      team.displayName,
    );
    if (leader) {
      await this.userModel
        .updateOne(
          { _id: userId },
          { $set: { allianceName: leader.allianceName } },
        )
        .exec();
    }
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

  /** Squad roster membership (authoritative), not only denormalized `user.playerTeamId`. */
  private async findSquadMembershipTeamId(
    userId: string,
  ): Promise<Types.ObjectId | null> {
    if (!Types.ObjectId.isValid(userId)) {
      return null;
    }
    const oid = new Types.ObjectId(userId);
    const team = await this.teamModel
      .findOne({ 'squadMembers.userId': oid })
      .select('_id')
      .lean<{ _id: Types.ObjectId }>()
      .exec();
    return team?._id ?? null;
  }

  private async assertUserNotInAnySquad(
    userId: string,
    exceptTeamId?: Types.ObjectId,
  ): Promise<void> {
    const existing = await this.findSquadMembershipTeamId(userId);
    if (existing && (!exceptTeamId || !existing.equals(exceptTeamId))) {
      throw new ConflictException('You already belong to a team');
    }
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
    const squadMembers = [{ userId: leaderOid, role: PlayerTeamMemberRole.R5 }];
    await this.teamModel
      .updateOne({ _id: team._id }, { $set: { squadMembers } })
      .exec();
    team.squadMembers = squadMembers as PlayerTeamDocument['squadMembers'];
  }

  async getPlayerTeamProfileFields(
    user: UserDocument,
  ): Promise<PlayerTeamProfileFields> {
    const squadTeamId = await this.findSquadMembershipTeamId(
      user._id.toString(),
    );
    const teamOid = squadTeamId ?? user.playerTeamId;
    if (!teamOid) {
      return {
        playerTeamId: null,
        playerTeamTag: null,
        playerTeamDisplayName: null,
        playerTeamLeaderUserId: null,
        isPlayerTeamLeader: false,
        pendingPlayerTeamJoinRequests: 0,
        playerTeamSquadRole: null,
      };
    }
    const team = await this.teamModel.findById(teamOid).exec();
    if (!team) {
      return {
        playerTeamId: null,
        playerTeamTag: null,
        playerTeamDisplayName: null,
        playerTeamLeaderUserId: null,
        isPlayerTeamLeader: false,
        pendingPlayerTeamJoinRequests: 0,
        playerTeamSquadRole: null,
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
    const squadRole = this.resolveSquadRoleForMember(team, uid);
    return {
      playerTeamId: team._id.toString(),
      playerTeamTag: team.tag,
      playerTeamDisplayName: team.displayName,
      playerTeamLeaderUserId: leaderId,
      isPlayerTeamLeader: isLeader,
      pendingPlayerTeamJoinRequests: pending,
      playerTeamSquadRole: squadRole,
    };
  }

  /**
   * Align `users.playerTeamId` and all `gameIdentities[].playerTeamId` with squad roster.
   * No-op when already consistent.
   */
  async reconcileSquadTeamBindingForUser(
    userId: string,
  ): Promise<UserDocument | null> {
    if (!Types.ObjectId.isValid(userId)) {
      return null;
    }
    const user = await this.userModel.findById(userId).exec();
    if (!user) {
      return null;
    }
    const squadTeamId = await this.findSquadMembershipTeamId(userId);
    if (!squadTeamId) {
      const hasTeamLink =
        Boolean(user.playerTeamId) ||
        (user.gameIdentities ?? []).some((g) => g.playerTeamId != null);
      if (!hasTeamLink) {
        return user;
      }
      return this.gameIdentities.bindAllIdentitiesToTeam(
        userId,
        null,
        null,
        null,
      );
    }
    const team = await this.teamModel.findById(squadTeamId).exec();
    if (!team) {
      return user;
    }
    await this.migrateLegacyIfNeeded(team);
    const teamOid = team._id;
    const needsBind =
      !user.playerTeamId?.equals(teamOid) ||
      (user.gameIdentities ?? []).some(
        (g) => !g.playerTeamId?.equals(teamOid),
      ) ||
      user.teamTag !== team.tag ||
      user.teamDisplayName !== team.displayName;
    if (!needsBind) {
      return user;
    }
    return this.gameIdentities.bindAllIdentitiesToTeam(
      userId,
      teamOid,
      team.tag,
      team.displayName,
    );
  }

  private assertMember(team: PlayerTeamDocument, userId: string): void {
    const ok = team.squadMembers.some((m) =>
      squadMemberUserIdEquals(m.userId, userId),
    );
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

  async assertSquadOfficerOrThrow(
    teamId: string,
    userId: string,
  ): Promise<void> {
    const team = await this.getTeamIfMemberOrThrow(teamId, userId);
    const role = this.getSquadRoleForUser(team, userId);
    if (!isSquadOfficerRole(role)) {
      throw new ForbiddenException(
        'Only squad ranks R4 and R5 can perform this action',
      );
    }
  }

  getSquadRoleForUser(
    team: PlayerTeamDocument,
    userId: string,
  ): PlayerTeamMemberRole | null {
    const m = team.squadMembers.find((x) =>
      squadMemberUserIdEquals(x.userId, userId),
    );
    return m?.role ?? null;
  }

  /** Stored squad role, or R5 for [PlayerTeamDocument.leaderUserId]. */
  resolveSquadRoleForMember(
    team: PlayerTeamDocument,
    userId: string,
  ): PlayerTeamMemberRole {
    if (team.leaderUserId.toString() === userId) {
      return PlayerTeamMemberRole.R5;
    }
    return this.getSquadRoleForUser(team, userId) ?? PlayerTeamMemberRole.R1;
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

    const rosterTeams =
      validOids.length === 0
        ? []
        : await this.teamModel
            .find({ 'squadMembers.userId': { $in: validOids } })
            .exec();

    const teamIdByUserId = new Map<string, string>();
    for (const team of rosterTeams) {
      await this.migrateLegacyIfNeeded(team);
      const teamIdStr = team._id.toString();
      for (const m of team.squadMembers) {
        teamIdByUserId.set(m.userId.toString(), teamIdStr);
      }
    }

    const teamById = new Map(rosterTeams.map((t) => [t._id.toString(), t]));

    for (const id of unique) {
      if (!Types.ObjectId.isValid(id)) {
        out.set(id, PlayerTeamMemberRole.R1);
        continue;
      }
      const teamId = teamIdByUserId.get(id);
      if (!teamId) {
        out.set(id, PlayerTeamMemberRole.R1);
        continue;
      }
      const team = teamById.get(teamId);
      if (!team) {
        out.set(id, PlayerTeamMemberRole.R1);
        continue;
      }
      out.set(id, this.resolveSquadRoleForMember(team, id));
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
    await this.assertUserNotInAnySquad(userId);
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
        squadMembers: [{ userId: leaderOid, role: PlayerTeamMemberRole.R5 }],
      });
    } catch (e: unknown) {
      const code = (e as { code?: number })?.code;
      if (code === 11000) {
        throw new ConflictException('This team tag is already taken');
      }
      throw e;
    }
    await this.ensurePlayerTeamChatRooms(team);
    await this.gameIdentities.bindAllIdentitiesToTeam(
      userId,
      team._id,
      team.tag,
      team.displayName,
    );
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
    const teamIdStr = team._id.toString();
    const users = await this.userModel
      .find({ _id: { $in: team.squadMembers.map((m) => m.userId) } })
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
          username: this.gameIdentities.resolveMemberDisplayNickname(
            u,
            teamIdStr,
          ),
          role: u.role,
          telegramUsername: u.telegramUsername ?? null,
          presenceStatus: u.presenceStatus ?? null,
          lastPresenceAt: toIso(u.lastPresenceAt),
          lastAppActiveAt: toIso(u.lastAppActiveAt),
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
        accountRole: normalizeAllianceRole(row?.role ?? AllianceRole.MEMBER),
        teamRole,
        telegramUsername: row?.telegramUsername ?? null,
        presenceStatus: row?.presenceStatus ?? null,
        lastPresenceAt: row?.lastPresenceAt ?? null,
        lastAppActiveAt: row?.lastAppActiveAt ?? null,
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

  /** Matches Android [OVERLAY_INGAME_PRESENCE_STALE_MS] and UsersService.OVERLAY_INGAME_LIST_STALE_MS. */
  static readonly OVERLAY_PRESENCE_LIST_STALE_MS = 90_000;

  private isOverlayIngameRow(
    row: TeamMemberRow,
    nowMs: number,
    staleMs: number,
  ): boolean {
    if ((row.presenceStatus ?? '').trim().toLowerCase() !== 'ingame') {
      return false;
    }
    const at = row.lastPresenceAt ? Date.parse(row.lastPresenceAt) : Number.NaN;
    return !Number.isNaN(at) && nowMs - at <= staleMs;
  }

  private isRecentlyActiveOverlayRow(
    row: TeamMemberRow,
    nowMs: number,
    staleMs: number,
  ): boolean {
    if ((row.presenceStatus ?? '').trim().toLowerCase() === 'ingame') {
      return false;
    }
    const at = row.lastPresenceAt ? Date.parse(row.lastPresenceAt) : Number.NaN;
    return !Number.isNaN(at) && nowMs - at <= staleMs;
  }

  /**
   * Overlay «Участники онлайн»: only members with a fresh lastPresenceAt ping.
   */
  async getTeamOverlayPresence(
    teamId: string,
    requesterUserId: string,
  ): Promise<{
    ingame: TeamMemberRow[];
    recentlyActive: TeamMemberRow[];
  }> {
    const detail = await this.getTeamDetailForUser(teamId, requesterUserId);
    const nowMs = Date.now();
    const staleMs = TeamsService.OVERLAY_PRESENCE_LIST_STALE_MS;
    const ingame: TeamMemberRow[] = [];
    const recentlyActive: TeamMemberRow[] = [];
    for (const row of detail.members) {
      if (this.isOverlayIngameRow(row, nowMs, staleMs)) {
        ingame.push(row);
      } else if (this.isRecentlyActiveOverlayRow(row, nowMs, staleMs)) {
        recentlyActive.push(row);
      }
    }
    const byRankThenName = (a: TeamMemberRow, b: TeamMemberRow) => {
      const dr = squadRoleRank(b.teamRole) - squadRoleRank(a.teamRole);
      if (dr !== 0) return dr;
      return a.username.localeCompare(b.username, 'ru', {
        sensitivity: 'base',
      });
    };
    ingame.sort(byRankThenName);
    recentlyActive.sort(byRankThenName);
    return { ingame, recentlyActive };
  }

  async getUserPresenceBroadcastRow(userId: string): Promise<{
    userId: string;
    playerTeamId: string | null;
    presenceStatus: string | null;
    lastPresenceAt: string | null;
  } | null> {
    if (!Types.ObjectId.isValid(userId)) return null;
    const u = await this.userModel
      .findById(userId)
      .select('playerTeamId presenceStatus lastPresenceAt')
      .lean<{
        playerTeamId?: Types.ObjectId | null;
        presenceStatus?: string | null;
        lastPresenceAt?: Date | null;
      }>()
      .exec();
    if (!u) return null;
    const toIso = (v: Date | null | undefined): string | null => {
      if (v == null) return null;
      if (v instanceof Date) return v.toISOString();
      return null;
    };
    return {
      userId,
      playerTeamId: u.playerTeamId?.toString() ?? null,
      presenceStatus: u.presenceStatus ?? null,
      lastPresenceAt: toIso(u.lastPresenceAt),
    };
  }

  /** Active game server from profile; null when no valid identity. */
  private resolveActiveServerNumber(user: UserDocument | null): number | null {
    if (!user) return null;
    return this.gameIdentities.resolveSenderServerNumber(user);
  }

  /** Team ids with at least one member identity on [serverNumber]. */
  private async teamIdsOnServer(
    serverNumber: number,
  ): Promise<Types.ObjectId[]> {
    const rows = await this.userModel
      .aggregate<{ _id: Types.ObjectId }>([
        { $unwind: '$gameIdentities' },
        {
          $match: {
            'gameIdentities.serverNumber': serverNumber,
            'gameIdentities.playerTeamId': { $ne: null },
          },
        },
        { $group: { _id: '$gameIdentities.playerTeamId' } },
      ])
      .exec();
    return rows
      .map((r) => r._id)
      .filter((id): id is Types.ObjectId => id instanceof Types.ObjectId);
  }

  private async assertTeamHasMemberOnServer(
    teamId: Types.ObjectId,
    serverNumber: number,
  ): Promise<void> {
    const teamServers = await this.gameIdentities.collectServerNumbersForTeam(
      teamId.toString(),
    );
    if (!teamServers.includes(serverNumber)) {
      throw new BadRequestException('TEAM_JOIN_SERVER_MISMATCH');
    }
  }

  async searchTeams(q: string, requesterUserId: string, limit = 20) {
    const term = q?.trim() ?? '';
    if (term.length < 1) {
      return [];
    }
    const requester = await this.userModel.findById(requesterUserId).exec();
    const serverNumber = this.resolveActiveServerNumber(requester);
    if (serverNumber == null) {
      return [];
    }
    const teamIds = await this.teamIdsOnServer(serverNumber);
    if (teamIds.length === 0) {
      return [];
    }
    const esc = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    const rx = new RegExp(esc, 'i');
    const rows = await this.teamModel
      .find({
        _id: { $in: teamIds },
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
    await this.assertUserNotInAnySquad(requesterUserId);
    const rid = new Types.ObjectId(requesterUserId);
    if (
      team.squadMembers.some((m) =>
        squadMemberUserIdEquals(m.userId, requesterUserId),
      )
    ) {
      throw new ConflictException('Already a member');
    }
    const serverNumber = this.resolveActiveServerNumber(requester);
    if (serverNumber == null) {
      throw new BadRequestException('ACTIVE_GAME_SERVER_REQUIRED');
    }
    await this.assertTeamHasMemberOnServer(team._id, serverNumber);
    const dup = await this.joinRequestModel.findOne({
      teamId: team._id,
      requesterUserId: rid,
      status: TeamJoinRequestStatus.PENDING,
    });
    if (dup) {
      return { id: dup._id.toString(), alreadyPending: true };
    }
    try {
      const doc = await this.joinRequestModel.create({
        teamId: team._id,
        requesterUserId: rid,
        status: TeamJoinRequestStatus.PENDING,
      });
      return { id: doc._id.toString(), alreadyPending: false };
    } catch (e: unknown) {
      const code = (e as { code?: number })?.code;
      if (code === 11000) {
        const raced = await this.joinRequestModel.findOne({
          teamId: team._id,
          requesterUserId: rid,
          status: TeamJoinRequestStatus.PENDING,
        });
        if (raced) {
          return { id: raced._id.toString(), alreadyPending: true };
        }
      }
      throw e;
    }
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
          teamId: Types.ObjectId;
          requesterUserId: Types.ObjectId;
          createdAt?: Date;
        }>
      >()
      .exec();
    const userIds = [...new Set(reqs.map((r) => r.requesterUserId.toString()))];
    const users = await this.userModel
      .find({ _id: { $in: userIds.map((id) => new Types.ObjectId(id)) } })
      .exec();
    const byId = new Map(users.map((u) => [u._id.toString(), u]));
    return reqs.map((r) => {
      const teamIdStr = r.teamId?.toString() ?? '';
      const requester = byId.get(r.requesterUserId.toString());
      const displayName = requester
        ? this.gameIdentities.resolveMemberDisplayNickname(requester, teamIdStr)
        : '?';
      return {
        id: r._id.toString(),
        requesterUserId: r.requesterUserId.toString(),
        requesterUsername: displayName,
        createdAt: (r.createdAt ?? new Date()).toISOString(),
      };
    });
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
    await this.assertUserNotInAnySquad(requesterId, team._id);
    const serverNumber = this.resolveActiveServerNumber(requester);
    if (serverNumber == null) {
      throw new BadRequestException('ACTIVE_GAME_SERVER_REQUIRED');
    }
    await this.assertTeamHasMemberOnServer(team._id, serverNumber);
    const rid = new Types.ObjectId(requesterId);
    if (
      team.squadMembers.some((m) =>
        squadMemberUserIdEquals(m.userId, requesterId),
      )
    ) {
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
    await this.assertUserNotInAnySquad(target._id.toString());
    const tid = new Types.ObjectId(target._id.toString());
    if (
      team.squadMembers.some((m) =>
        squadMemberUserIdEquals(m.userId, target._id.toString()),
      )
    ) {
      throw new ConflictException('Already a member');
    }
    const serverNumber = this.resolveActiveServerNumber(target);
    if (serverNumber == null) {
      throw new BadRequestException('ACTIVE_GAME_SERVER_REQUIRED');
    }
    await this.assertTeamHasMemberOnServer(team._id, serverNumber);
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
    if (
      !team.squadMembers.some((m) =>
        squadMemberUserIdEquals(m.userId, memberUserId),
      )
    ) {
      throw new NotFoundException('Member not on this team');
    }
    await this.teamModel
      .updateOne(
        { _id: team._id },
        { $pull: { squadMembers: { userId: mid } } },
      )
      .exec();
    await this.gameIdentities.clearPlayerTeamForTeam(memberUserId, team._id);
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
      team._id,
    );
    if (nameOther) {
      throw new ConflictException('This team name is already taken');
    }
    const tagOther = await this.findTeamByTag(tagNorm, team._id);
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
    await this.chatRoomsService.ensureAllianceChatRoomsForScope(
      playerTeamChatAllianceId(team._id.toString()),
      nameVal,
    );
    return { ok: true };
  }

  /** R5 admin: rename team tag/display name without leader check. */
  async updateTeamBrandingForAdmin(
    teamId: string,
    rawName?: string,
    rawTag?: string,
  ): Promise<{ ok: true }> {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    await this.migrateLegacyIfNeeded(team);

    let nameVal = team.displayName;
    let tagNorm = team.tag;

    if (rawName != null) {
      const nameTrim = rawName.trim();
      if (nameTrim.length < 2) {
        throw new BadRequestException('Team name is too short');
      }
      nameVal = nameTrim.slice(0, 48);
      const nameOther = await this.findTeamByDisplayNameCaseInsensitive(
        nameVal,
        team._id,
      );
      if (nameOther) {
        throw new ConflictException('This team name is already taken');
      }
    }

    if (rawTag != null) {
      tagNorm = this.normalizeTag(rawTag);
      const tagOther = await this.findTeamByTag(tagNorm, team._id);
      if (tagOther) {
        throw new ConflictException('This team tag is already taken');
      }
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
    await this.chatRoomsService.ensureAllianceChatRoomsForScope(
      playerTeamChatAllianceId(team._id.toString()),
      nameVal,
    );
    return { ok: true };
  }

  async updateMemberSquadRole(
    teamId: string,
    actorUserId: string,
    targetUserId: string,
    rawRole: string,
  ): Promise<{ ok: true }> {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    await this.migrateLegacyIfNeeded(team);
    this.assertMember(team, actorUserId);

    if (team.leaderUserId.toString() === targetUserId) {
      throw new BadRequestException('Cannot change the leader squad role');
    }

    const roleNorm = rawRole.trim().toUpperCase();
    if (
      !Object.values(PlayerTeamMemberRole).includes(
        roleNorm as PlayerTeamMemberRole,
      )
    ) {
      throw new BadRequestException('Invalid squad role');
    }
    const role = roleNorm as PlayerTeamMemberRole;

    const actorRole = this.resolveSquadRoleForMember(team, actorUserId);
    const actorRank = squadRoleRank(actorRole);
    if (actorRank < squadRoleRank(PlayerTeamMemberRole.R4)) {
      throw new ForbiddenException(
        'Only squad roles R4 and R5 can change member ranks',
      );
    }

    const allowed =
      actorRank >= squadRoleRank(PlayerTeamMemberRole.R5)
        ? SQUAD_ROLES_ASSIGNABLE_BY_R5
        : SQUAD_ROLES_ASSIGNABLE_BY_R4;
    if (!allowed.includes(role)) {
      throw new ForbiddenException('Only squad role R5 can assign rank R5');
    }

    const targetRole = this.resolveSquadRoleForMember(team, targetUserId);
    if (
      actorRank === squadRoleRank(PlayerTeamMemberRole.R4) &&
      squadRoleRank(targetRole) >= squadRoleRank(PlayerTeamMemberRole.R5)
    ) {
      throw new ForbiddenException(
        'Only squad role R5 can change the rank of an R5 member',
      );
    }

    const tid = new Types.ObjectId(targetUserId);
    if (
      !team.squadMembers.some((m) =>
        squadMemberUserIdEquals(m.userId, targetUserId),
      )
    ) {
      throw new NotFoundException('Member not on this team');
    }
    const res = await this.teamModel
      .updateOne(
        { _id: team._id },
        { $set: { 'squadMembers.$[m].role': role } },
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

  async listAllTeamsForAdmin(opts?: {
    serverNumber?: number;
    skip?: number;
    limit?: number;
  }): Promise<PaginatedResult<PlayerTeamAdminRow>> {
    const { skip, limit } = paginateParams(opts?.skip, opts?.limit);

    let teamFilter: Record<string, unknown> = {};
    if (opts?.serverNumber != null) {
      const teamIds = await this.gameIdentities.findTeamIdsWithMemberOnServer(
        opts.serverNumber,
      );
      teamFilter = { _id: { $in: teamIds } };
    }

    const [teams, total] = await Promise.all([
      this.teamModel
        .find(teamFilter)
        .sort({ displayName: 1, tag: 1 })
        .skip(skip)
        .limit(limit)
        .exec(),
      this.teamModel.countDocuments(teamFilter).exec(),
    ]);

    const teamIdStrs = teams.map((t) => t._id.toString());
    const serverMap =
      await this.gameIdentities.collectServerNumbersForTeams(teamIdStrs);

    const leaderIds = teams.map((t) => t.leaderUserId);
    const leaders = await this.userModel
      .find({ _id: { $in: leaderIds } })
      .exec();
    const leaderById = new Map(leaders.map((u) => [u._id.toString(), u]));

    const memberRoutes = await this.userModel
      .aggregate<{ _id: Types.ObjectId; routes: string[] }>([
        {
          $match: {
            playerTeamId: {
              $in: teams.map((t) => t._id),
            },
          },
        },
        {
          $group: {
            _id: '$playerTeamId',
            routes: { $addToSet: '$allianceName' },
          },
        },
      ] as import('mongoose').PipelineStage[])
      .exec();
    const routesByTeam = new Map(
      memberRoutes.map((r) => [
        r._id.toString(),
        (r.routes ?? []).map((x) => x?.trim()).filter(Boolean),
      ]),
    );

    const items: PlayerTeamAdminRow[] = [];
    for (const team of teams) {
      await this.migrateLegacyIfNeeded(team);
      const teamIdStr = team._id.toString();
      const serverNumbers = serverMap.get(teamIdStr) ?? [];
      const leader = leaderById.get(team.leaderUserId.toString());
      const routes = routesByTeam.get(teamIdStr) ?? [];
      items.push({
        id: teamIdStr,
        tag: team.tag,
        displayName: team.displayName,
        leaderUserId: team.leaderUserId.toString(),
        leaderUsername: leader
          ? this.gameIdentities.resolveMemberDisplayNickname(leader, teamIdStr)
          : '—',
        leaderServerNumber: leader
          ? this.gameIdentities.resolveServerNumberForTeam(leader, teamIdStr)
          : null,
        serverNumbers,
        memberCount: team.squadMembers.length,
        chatRoutingSummary: routes.length > 0 ? routes.join(', ') : '—',
      });
    }

    return {
      items,
      total,
      skip,
      limit,
      hasMore: skip + items.length < total,
    };
  }

  async assertTeamExistsForAdmin(teamId: string): Promise<PlayerTeamDocument> {
    if (!Types.ObjectId.isValid(teamId)) {
      throw new NotFoundException('Team not found');
    }
    const team = await this.teamModel.findById(teamId).exec();
    if (!team) {
      throw new NotFoundException('Team not found');
    }
    await this.migrateLegacyIfNeeded(team);
    return team;
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
      .exec();
    const teamIdStr = team._id.toString();
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
      const displayNick = this.gameIdentities.resolveMemberDisplayNickname(
        u,
        teamIdStr,
      );
      return {
        userId: uid,
        username: displayNick,
        email: u.email,
        isLeader: team.leaderUserId.equals(u._id),
        accountRole: normalizeAllianceRole(u.role),
        teamRole: roleByUserId.get(uid) ?? 'R1',
        telegramUsername: u.telegramUsername ?? null,
        presenceStatus: u.presenceStatus ?? null,
        lastPresenceAt: toIso(u.lastPresenceAt),
        lastAppActiveAt: toIso(u.lastAppActiveAt),
        membershipStatus: u.membershipStatus ?? 'active',
        allianceName: u.allianceName?.trim() || '—',
        serverNumber: this.gameIdentities.resolveServerNumberForTeam(
          u,
          teamIdStr,
        ),
        gameNickname: displayNick,
        accountUsername: u.username,
        identityId: this.gameIdentities.resolveIdentityIdForTeam(u, teamIdStr),
        appVersionName: u.lastAppVersionName?.trim() || null,
        appVersionCode:
          typeof u.lastAppVersionCode === 'number' ? u.lastAppVersionCode : null,
        appVersionReportedAt: toIso(u.lastAppVersionReportedAt),
      };
    });
    members.sort(
      (a, b) => squadRoleRank(b.teamRole) - squadRoleRank(a.teamRole),
    );
    return {
      id: team._id.toString(),
      tag: team.tag,
      displayName: team.displayName,
      leaderUserId: team.leaderUserId.toString(),
      members,
    };
  }
}
