import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, PipelineStage, Types } from 'mongoose';
import {
  paginateParams,
  type PaginatedResult,
} from '../common/dto/paginated-result.dto';
import { normalizeAllianceRole } from '../common/alliance-role.util';
import {
  GameIdentity,
  GameIdentityDocument,
} from './schemas/game-identity.schema';
import { PlayerTeam, PlayerTeamDocument } from './schemas/player-team.schema';
import { User, UserDocument } from './schemas/user.schema';

export type AdminServerSummary = {
  serverNumber: number;
  userCount: number;
};

export type AdminUserOnServerRow = {
  userId: string;
  identityId: string;
  accountUsername: string;
  email: string;
  serverNumber: number;
  gameNickname: string;
  playerTeamId: string | null;
  playerTeamTag: string | null;
  playerTeamDisplayName: string | null;
  isActiveIdentity: boolean;
  accountRole: string;
  membershipStatus: string;
  appVersionName: string | null;
  appVersionCode: number | null;
  appVersionReportedAt: string | null;
};

export type SafeGameIdentity = {
  id: string;
  serverNumber: number;
  gameNickname: string;
  playerTeamId: string | null;
  playerTeamTag: string | null;
  playerTeamDisplayName: string | null;
};

@Injectable()
export class GameIdentitiesService {
  constructor(
    @InjectModel(User.name) private readonly userModel: Model<User>,
    @InjectModel(PlayerTeam.name)
    private readonly teamModel: Model<PlayerTeam>,
  ) {}

  normalizeServerNumber(raw: number): number {
    const n = Math.floor(Number(raw));
    if (!Number.isFinite(n) || n < 1 || n > 9999) {
      throw new BadRequestException('Server number must be between 1 and 9999');
    }
    return n;
  }

  normalizeGameNickname(raw: string): string {
    const trimmed = raw.trim();
    if (trimmed.length < 2 || trimmed.length > 32) {
      throw new BadRequestException('Game nickname must be 2–32 characters');
    }
    return trimmed;
  }

  private escapeRegex(s: string): string {
    return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  async findNicknameOnServer(
    serverNumber: number,
    gameNickname: string,
    excludeUserId?: string,
  ): Promise<UserDocument | null> {
    const esc = this.escapeRegex(gameNickname);
    const filter: Record<string, unknown> = {
      gameIdentities: {
        $elemMatch: {
          serverNumber,
          gameNickname: new RegExp(`^${esc}$`, 'i'),
        },
      },
    };
    if (excludeUserId && Types.ObjectId.isValid(excludeUserId)) {
      filter._id = { $ne: new Types.ObjectId(excludeUserId) };
    }
    return this.userModel.findOne(filter).exec();
  }

  private identityId(
    identity: GameIdentity & { _id?: Types.ObjectId },
  ): string {
    return identity._id?.toString() ?? '';
  }

  getActiveIdentity(
    user: UserDocument,
  ): (GameIdentity & { _id: Types.ObjectId }) | null {
    const list = user.gameIdentities ?? [];
    if (!list.length) return null;
    const activeId = user.activeGameIdentityId?.toString();
    if (activeId) {
      const found = list.find((g) => this.identityId(g) === activeId);
      if (found?._id) {
        return found as GameIdentity & { _id: Types.ObjectId };
      }
    }
    const first = list[0];
    return first?._id
      ? (first as GameIdentity & { _id: Types.ObjectId })
      : null;
  }

  /** Миграция: одна запись из legacy username + playerTeamId. */
  async ensureMigrated(user: UserDocument): Promise<UserDocument> {
    if ((user.gameIdentities?.length ?? 0) > 0) {
      return this.syncUserFromActiveIdentity(user);
    }
    const serverNumber = 1;
    const gameNickname = this.normalizeGameNickname(user.username);
    const conflict = await this.findNicknameOnServer(
      serverNumber,
      gameNickname,
      user._id.toString(),
    );
    const nick =
      conflict != null
        ? `${gameNickname}_${user._id.toString().slice(-4)}`
        : gameNickname;
    const identityId = new Types.ObjectId();
    const updated = await this.userModel
      .findByIdAndUpdate(
        user._id,
        {
          $set: {
            gameIdentities: [
              {
                _id: identityId,
                serverNumber,
                gameNickname: nick,
                playerTeamId: user.playerTeamId ?? null,
              },
            ],
            activeGameIdentityId: identityId,
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return this.syncUserFromActiveIdentity(updated);
  }

  async createInitialIdentity(
    user: UserDocument,
    serverNumber: number,
    gameNickname: string,
  ): Promise<UserDocument> {
    const server = this.normalizeServerNumber(serverNumber);
    const nick = this.normalizeGameNickname(gameNickname);
    const taken = await this.findNicknameOnServer(
      server,
      nick,
      user._id.toString(),
    );
    if (taken) {
      throw new ConflictException(
        'This game nickname is already taken on this server',
      );
    }
    const identityId = new Types.ObjectId();
    const updated = await this.userModel
      .findByIdAndUpdate(
        user._id,
        {
          $set: {
            gameIdentities: [
              {
                _id: identityId,
                serverNumber: server,
                gameNickname: nick,
                playerTeamId: null,
              },
            ],
            activeGameIdentityId: identityId,
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return this.syncUserFromActiveIdentity(updated);
  }

  async syncUserFromActiveIdentity(user: UserDocument): Promise<UserDocument> {
    const active = this.getActiveIdentity(user);
    const playerTeamId = active?.playerTeamId ?? null;
    let teamTag: string | null = null;
    let teamDisplayName: string | null = null;
    if (playerTeamId) {
      const team = await this.teamModel.findById(playerTeamId).exec();
      if (team) {
        teamTag = team.tag;
        teamDisplayName = team.displayName;
      }
    }
    const refreshed = await this.userModel
      .findByIdAndUpdate(
        user._id,
        {
          $set: {
            playerTeamId,
            teamTag,
            teamDisplayName,
            ...(active
              ? { activeGameIdentityId: active._id }
              : { activeGameIdentityId: null }),
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    return refreshed ?? user;
  }

  resolveSenderUsername(user: UserDocument): string {
    const active = this.getActiveIdentity(user);
    const activeNick = active?.gameNickname?.trim();
    if (activeNick && !this.looksLikeAccountEmail(activeNick)) {
      return activeNick;
    }
    for (const identity of user.gameIdentities ?? []) {
      const nick = identity.gameNickname?.trim();
      if (nick && !this.looksLikeAccountEmail(nick)) {
        return nick;
      }
    }
    const legacy = user.username?.trim() ?? '';
    if (legacy && !this.looksLikeAccountEmail(legacy)) {
      return legacy;
    }
    return '';
  }

  /** Account login is stored in username/email — never show it as a public nickname. */
  looksLikeAccountEmail(value: string): boolean {
    const v = value.trim().toLowerCase();
    if (!v.includes('@')) return false;
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v);
  }

  resolvePublicDisplayName(
    user: UserDocument,
    teamId?: string | null,
  ): string {
    const team = teamId?.trim();
    const resolved = team
      ? this.resolveMemberDisplayNickname(user, team)
      : this.resolveSenderUsername(user);
    if (resolved && !this.looksLikeAccountEmail(resolved)) {
      return resolved;
    }
    return 'Союзник';
  }

  coalesceDisplayName(
    stored: string | null | undefined,
    resolved: string | null | undefined,
  ): string {
    const storedTrim = stored?.trim() ?? '';
    if (storedTrim && !this.looksLikeAccountEmail(storedTrim)) {
      return storedTrim;
    }
    const resolvedTrim = resolved?.trim() ?? '';
    if (resolvedTrim && !this.looksLikeAccountEmail(resolvedTrim)) {
      return resolvedTrim;
    }
    return 'Союзник';
  }

  async buildSenderDisplayNameMap(
    userIds: string[],
  ): Promise<Map<string, string>> {
    const unique = [...new Set(userIds.filter(Boolean))];
    const out = new Map<string, string>();
    if (!unique.length) return out;
    const users = await this.userModel
      .find({
        _id: { $in: unique.map((id) => new Types.ObjectId(id)) },
      })
      .exec();
    for (const user of users) {
      const teamId = user.playerTeamId?.toString() ?? null;
      out.set(user._id.toString(), this.resolvePublicDisplayName(user, teamId));
    }
    return out;
  }

  resolveSenderServerNumber(user: UserDocument): number | null {
    const active = this.getActiveIdentity(user);
    const n = active?.serverNumber;
    return n != null && n >= 1 ? n : null;
  }

  private identitiesOnTeam(
    user: UserDocument,
    teamId: string,
  ): (GameIdentity & { _id?: Types.ObjectId })[] {
    const teamOid = Types.ObjectId.isValid(teamId)
      ? new Types.ObjectId(teamId)
      : null;
    if (!teamOid) return [];
    return (user.gameIdentities ?? []).filter((g) =>
      g.playerTeamId?.equals(teamOid),
    );
  }

  resolveServerNumberForTeam(
    user: UserDocument,
    teamId: string,
  ): number | null {
    const onTeam = this.identitiesOnTeam(user, teamId);
    if (onTeam.length === 0) {
      return this.resolveSenderServerNumber(user);
    }
    const activeServer = this.resolveSenderServerNumber(user);
    if (activeServer != null) {
      const onActiveServer = onTeam.find(
        (g) => g.serverNumber === activeServer,
      );
      if (
        onActiveServer?.serverNumber != null &&
        onActiveServer.serverNumber >= 1
      ) {
        return onActiveServer.serverNumber;
      }
    }
    const first = onTeam.find((g) => g.serverNumber >= 1);
    return first?.serverNumber ?? null;
  }

  resolveMemberDisplayNickname(user: UserDocument, teamId: string): string {
    const onTeam = this.identitiesOnTeam(user, teamId);
    if (onTeam.length === 0) {
      return this.resolveSenderUsername(user);
    }
    const activeServer = this.resolveSenderServerNumber(user);
    if (activeServer != null) {
      const onActiveServer = onTeam.find(
        (g) => g.serverNumber === activeServer,
      );
      const nick = onActiveServer?.gameNickname?.trim();
      if (nick && !this.looksLikeAccountEmail(nick)) return nick;
    }
    const any = onTeam
      .map((g) => g.gameNickname?.trim())
      .find((nick) => nick && !this.looksLikeAccountEmail(nick));
    return any ?? this.resolveSenderUsername(user);
  }

  /**
   * Bind squad team on every game identity (authoritative for chat + profile).
   * When [teamId] is null, clears team on all identities.
   */
  async bindAllIdentitiesToTeam(
    userId: string,
    teamId: Types.ObjectId | null,
    teamTag: string | null,
    teamDisplayName: string | null,
  ): Promise<UserDocument | null> {
    const user = await this.userModel.findById(userId).exec();
    if (!user) return null;
    const migrated =
      (user.gameIdentities?.length ?? 0) > 0
        ? user
        : await this.ensureMigrated(user);
    const identities = (migrated.gameIdentities ?? []).map((g) => ({
      _id: g._id,
      serverNumber: g.serverNumber,
      gameNickname: g.gameNickname,
      playerTeamId: teamId,
    }));
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        {
          $set: {
            gameIdentities: identities,
            playerTeamId: teamId,
            teamTag: teamId ? teamTag : null,
            teamDisplayName: teamId ? teamDisplayName : null,
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) return null;
    return this.syncUserFromActiveIdentity(updated);
  }

  async setPlayerTeamOnActive(
    userId: string,
    teamId: Types.ObjectId | null,
    teamTag: string | null,
    teamDisplayName: string | null,
  ): Promise<UserDocument | null> {
    return this.bindAllIdentitiesToTeam(
      userId,
      teamId,
      teamTag,
      teamDisplayName,
    );
  }

  async clearPlayerTeamOnActive(userId: string): Promise<UserDocument | null> {
    return this.setPlayerTeamOnActive(userId, null, null, null);
  }

  /** Снять привязку команды у всех ников пользователя на этой команде. */
  async clearPlayerTeamForTeam(
    userId: string,
    teamId: Types.ObjectId,
  ): Promise<UserDocument | null> {
    const user = await this.userModel.findById(userId).exec();
    if (!user) return null;
    const migrated = await this.ensureMigrated(user);
    const identities = (migrated.gameIdentities ?? []).map((g) => {
      if (g.playerTeamId?.equals(teamId)) {
        return {
          _id: g._id,
          serverNumber: g.serverNumber,
          gameNickname: g.gameNickname,
          playerTeamId: null,
        };
      }
      return {
        _id: g._id,
        serverNumber: g.serverNumber,
        gameNickname: g.gameNickname,
        playerTeamId: g.playerTeamId ?? null,
      };
    });
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        { $set: { gameIdentities: identities } },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) return null;
    return this.syncUserFromActiveIdentity(updated);
  }

  activeIdentityHasTeam(user: UserDocument): boolean {
    const active = this.getActiveIdentity(user);
    return Boolean(active?.playerTeamId);
  }

  async buildSafeIdentities(user: UserDocument): Promise<SafeGameIdentity[]> {
    const migrated = await this.ensureMigrated(user);
    const teamIds = [
      ...new Set(
        (migrated.gameIdentities ?? [])
          .map((g) => g.playerTeamId?.toString())
          .filter((v): v is string => Boolean(v)),
      ),
    ];
    const teams =
      teamIds.length === 0
        ? []
        : await this.teamModel
            .find({
              _id: {
                $in: teamIds.map((id) => new Types.ObjectId(id)),
              },
            })
            .select('tag displayName')
            .lean<
              Array<{
                _id: Types.ObjectId;
                tag: string;
                displayName: string;
              }>
            >()
            .exec();
    const teamById = new Map(teams.map((t) => [t._id.toString(), t]));
    return (migrated.gameIdentities ?? []).map((g) => {
      const tid = g.playerTeamId?.toString() ?? null;
      const team = tid ? teamById.get(tid) : undefined;
      return {
        id: this.identityId(g),
        serverNumber: g.serverNumber,
        gameNickname: g.gameNickname,
        playerTeamId: tid,
        playerTeamTag: team?.tag ?? null,
        playerTeamDisplayName: team?.displayName ?? null,
      };
    });
  }

  async addIdentity(
    userId: string,
    serverNumber: number,
    gameNickname: string,
  ): Promise<UserDocument> {
    const user = await this.userModel.findById(userId).exec();
    if (!user) throw new NotFoundException('User not found');
    const migrated = await this.ensureMigrated(user);
    const server = this.normalizeServerNumber(serverNumber);
    const nick = this.normalizeGameNickname(gameNickname);
    const dupServer = (migrated.gameIdentities ?? []).some(
      (g) => g.serverNumber === server,
    );
    if (dupServer) {
      throw new ConflictException('You already have a nickname on this server');
    }
    const taken = await this.findNicknameOnServer(server, nick, userId);
    if (taken) {
      throw new ConflictException(
        'This game nickname is already taken on this server',
      );
    }
    const identityId = new Types.ObjectId();
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        {
          $push: {
            gameIdentities: {
              _id: identityId,
              serverNumber: server,
              gameNickname: nick,
              playerTeamId: null,
            },
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) throw new NotFoundException('User not found');
    return updated;
  }

  /** First game identity for legacy accounts (admin). */
  async adminBootstrapGameIdentity(
    userId: string,
    body: { serverNumber: number; gameNickname: string },
  ): Promise<UserDocument> {
    const user = await this.userModel.findById(userId).exec();
    if (!user) {
      throw new NotFoundException('User not found');
    }
    if ((user.gameIdentities?.length ?? 0) > 0) {
      throw new BadRequestException(
        'User already has a game identity; update the existing row instead',
      );
    }
    return this.createInitialIdentity(
      user,
      body.serverNumber,
      body.gameNickname,
    );
  }

  async updateIdentity(
    userId: string,
    identityId: string,
    patch: { serverNumber?: number; gameNickname?: string },
  ): Promise<UserDocument> {
    if (!Types.ObjectId.isValid(identityId)) {
      throw new NotFoundException('Game identity not found');
    }
    const user = await this.userModel.findById(userId).exec();
    if (!user) throw new NotFoundException('User not found');
    const migrated = await this.ensureMigrated(user);
    const current = (migrated.gameIdentities ?? []).find(
      (g) => this.identityId(g) === identityId,
    );
    if (!current) throw new NotFoundException('Game identity not found');

    const server =
      patch.serverNumber != null
        ? this.normalizeServerNumber(patch.serverNumber)
        : current.serverNumber;
    const nick =
      patch.gameNickname != null
        ? this.normalizeGameNickname(patch.gameNickname)
        : current.gameNickname;

    if (server !== current.serverNumber) {
      const dupServer = (migrated.gameIdentities ?? []).some(
        (g) => this.identityId(g) !== identityId && g.serverNumber === server,
      );
      if (dupServer) {
        throw new ConflictException(
          'You already have a nickname on this server',
        );
      }
    }
    if (
      server !== current.serverNumber ||
      nick.toLowerCase() !== current.gameNickname.toLowerCase()
    ) {
      const taken = await this.findNicknameOnServer(server, nick, userId);
      if (taken) {
        throw new ConflictException(
          'This game nickname is already taken on this server',
        );
      }
    }

    const identities = (migrated.gameIdentities ?? []).map((g) => {
      if (this.identityId(g) !== identityId) {
        return {
          _id: g._id,
          serverNumber: g.serverNumber,
          gameNickname: g.gameNickname,
          playerTeamId: g.playerTeamId ?? null,
        };
      }
      return {
        _id: g._id,
        serverNumber: server,
        gameNickname: nick,
        playerTeamId: g.playerTeamId ?? null,
      };
    });
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        { $set: { gameIdentities: identities } },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) throw new NotFoundException('User not found');
    return this.syncUserFromActiveIdentity(updated);
  }

  async removeIdentity(
    userId: string,
    identityId: string,
  ): Promise<UserDocument> {
    if (!Types.ObjectId.isValid(identityId)) {
      throw new NotFoundException('Game identity not found');
    }
    const user = await this.userModel.findById(userId).exec();
    if (!user) throw new NotFoundException('User not found');
    const migrated = await this.ensureMigrated(user);
    if ((migrated.gameIdentities?.length ?? 0) <= 1) {
      throw new BadRequestException('Cannot remove your only game nickname');
    }
    const wasActive = migrated.activeGameIdentityId?.toString() === identityId;
    const identities = (migrated.gameIdentities ?? []).filter(
      (g) => this.identityId(g) !== identityId,
    );
    const nextActiveId = wasActive
      ? (identities[0]?._id ?? null)
      : migrated.activeGameIdentityId;
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        {
          $set: {
            gameIdentities: identities,
            activeGameIdentityId: nextActiveId,
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) throw new NotFoundException('User not found');
    return this.syncUserFromActiveIdentity(updated);
  }

  async listServerSummariesForAdmin(): Promise<AdminServerSummary[]> {
    const rows = await this.userModel
      .aggregate<{ _id: number; count: number }>([
        { $unwind: '$gameIdentities' },
        {
          $group: {
            _id: '$gameIdentities.serverNumber',
            count: { $sum: 1 },
          },
        },
        { $sort: { _id: 1 } },
      ])
      .exec();
    return rows
      .filter((r) => r._id != null && r._id >= 1)
      .map((r) => ({
        serverNumber: r._id,
        userCount: r.count,
      }));
  }

  async collectServerNumbersForTeam(teamId: string): Promise<number[]> {
    if (!Types.ObjectId.isValid(teamId)) {
      return [];
    }
    const tid = new Types.ObjectId(teamId);
    const rows = await this.userModel
      .aggregate<{
        _id: number;
      }>([
        { $match: { 'gameIdentities.playerTeamId': tid } },
        { $unwind: '$gameIdentities' },
        { $match: { 'gameIdentities.playerTeamId': tid } },
        { $group: { _id: '$gameIdentities.serverNumber' } },
        { $sort: { _id: 1 } },
      ])
      .exec();
    return rows.map((r) => r._id).filter((n) => Number.isFinite(n) && n >= 1);
  }

  resolveIdentityIdForTeam(user: UserDocument, teamId: string): string | null {
    const list = user.gameIdentities ?? [];
    const onTeam = list.find((g) => g.playerTeamId?.toString() === teamId);
    if (onTeam?._id) {
      return this.identityId(onTeam);
    }
    if (user.playerTeamId?.toString() === teamId) {
      const active = this.getActiveIdentity(user);
      return active ? this.identityId(active) : null;
    }
    return null;
  }

  /** R5 admin / chat enrich: server number per sender when message has no stored value. */
  async buildSenderServerNumberMap(
    userIds: string[],
  ): Promise<Map<string, number | null>> {
    const unique = [...new Set(userIds.filter(Boolean))];
    const out = new Map<string, number | null>();
    if (!unique.length) return out;
    const users = await this.userModel
      .find({
        _id: { $in: unique.map((id) => new Types.ObjectId(id)) },
      })
      .exec();
    for (const user of users) {
      out.set(user._id.toString(), this.resolveSenderServerNumber(user));
    }
    return out;
  }

  /** Team ids that have at least one identity on [serverNumber]. */
  async findTeamIdsWithMemberOnServer(
    serverNumber: number,
  ): Promise<Types.ObjectId[]> {
    const rows = await this.userModel
      .aggregate<{ _id: Types.ObjectId }>([
        { $match: { 'gameIdentities.serverNumber': serverNumber } },
        { $unwind: '$gameIdentities' },
        { $match: { 'gameIdentities.serverNumber': serverNumber } },
        {
          $group: {
            _id: '$gameIdentities.playerTeamId',
          },
        },
        { $match: { _id: { $ne: null } } },
      ])
      .exec();
    return rows.map((r) => r._id).filter((id) => id instanceof Types.ObjectId);
  }

  async collectServerNumbersForTeams(
    teamIds: string[],
  ): Promise<Map<string, number[]>> {
    const out = new Map<string, number[]>();
    if (!teamIds.length) return out;
    const oids = teamIds
      .filter((id) => Types.ObjectId.isValid(id))
      .map((id) => new Types.ObjectId(id));
    if (!oids.length) return out;
    const rows = await this.userModel
      .aggregate<{ _id: { teamId: Types.ObjectId; server: number } }>([
        { $match: { 'gameIdentities.playerTeamId': { $in: oids } } },
        { $unwind: '$gameIdentities' },
        { $match: { 'gameIdentities.playerTeamId': { $in: oids } } },
        {
          $group: {
            _id: {
              teamId: '$gameIdentities.playerTeamId',
              server: '$gameIdentities.serverNumber',
            },
          },
        },
      ])
      .exec();
    for (const row of rows) {
      const tid = row._id?.teamId?.toString();
      const server = row._id?.server;
      if (!tid || server == null || server < 1) continue;
      const list = out.get(tid) ?? [];
      if (!list.includes(server)) list.push(server);
      out.set(
        tid,
        list.sort((a, b) => a - b),
      );
    }
    return out;
  }

  async listUsersForAdminByServer(opts: {
    serverNumber?: number;
    q?: string;
    withoutTeam?: boolean;
    skip?: number;
    limit?: number;
  }): Promise<PaginatedResult<AdminUserOnServerRow>> {
    const { skip, limit } = paginateParams(opts.skip, opts.limit);
    const qTrim = opts.q?.trim();
    const qLower = qTrim?.toLowerCase();

    const pipeline: Record<string, unknown>[] = [];
    if (qLower) {
      const esc = this.escapeRegex(qTrim ?? '');
      pipeline.push({
        $match: {
          $or: [
            { email: { $regex: esc, $options: 'i' } },
            { username: { $regex: esc, $options: 'i' } },
            { 'gameIdentities.gameNickname': { $regex: esc, $options: 'i' } },
          ],
        },
      });
    }
    pipeline.push({
      $unwind: {
        path: '$gameIdentities',
        preserveNullAndEmptyArrays: true,
      },
    });
    if (opts.serverNumber != null) {
      pipeline.push({
        $match: { 'gameIdentities.serverNumber': opts.serverNumber },
      });
    }
    if (opts.withoutTeam) {
      pipeline.push({
        $match: {
          $or: [
            {
              gameIdentities: null,
              $or: [
                { playerTeamId: null },
                { playerTeamId: { $exists: false } },
              ],
            },
            { 'gameIdentities.playerTeamId': null },
            { 'gameIdentities.playerTeamId': { $exists: false } },
          ],
        },
      });
    }
    pipeline.push({
      $lookup: {
        from: 'playerteams',
        localField: 'gameIdentities.playerTeamId',
        foreignField: '_id',
        as: 'teamDoc',
      },
    });
    pipeline.push({
      $addFields: {
        team: { $arrayElemAt: ['$teamDoc', 0] },
        activeIdStr: { $toString: '$activeGameIdentityId' },
        identityIdStr: {
          $cond: [
            { $ifNull: ['$gameIdentities._id', false] },
            { $toString: '$gameIdentities._id' },
            '',
          ],
        },
        rowServerNumber: {
          $ifNull: ['$gameIdentities.serverNumber', 0],
        },
        rowGameNickname: {
          $ifNull: ['$gameIdentities.gameNickname', '$username'],
        },
      },
    });
    pipeline.push({
      $project: {
        userId: { $toString: '$_id' },
        identityId: '$identityIdStr',
        email: 1,
        role: 1,
        membershipStatus: 1,
        serverNumber: '$rowServerNumber',
        gameNickname: '$rowGameNickname',
        playerTeamId: {
          $cond: [
            { $ifNull: ['$gameIdentities.playerTeamId', false] },
            { $toString: '$gameIdentities.playerTeamId' },
            null,
          ],
        },
        playerTeamTag: '$team.tag',
        playerTeamDisplayName: '$team.displayName',
        isActiveIdentity: {
          $cond: [
            { $eq: ['$gameIdentities', null] },
            false,
            { $eq: ['$activeIdStr', '$identityIdStr'] },
          ],
        },
        needsGameIdentity: { $eq: ['$gameIdentities', null] },
        lastAppVersionName: 1,
        lastAppVersionCode: 1,
        lastAppVersionReportedAt: 1,
      },
    });
    pipeline.push({
      $sort: { serverNumber: 1, gameNickname: 1 },
    });
    pipeline.push({
      $facet: {
        data: [{ $skip: skip }, { $limit: limit }],
        meta: [{ $count: 'total' }],
      },
    });

    const [facet] = await this.userModel
      .aggregate<{
        data: Array<{
          userId: string;
          identityId: string;
          email: string;
          role: string;
          membershipStatus?: string;
          serverNumber: number;
          gameNickname: string;
          playerTeamId: string | null;
          playerTeamTag?: string | null;
          playerTeamDisplayName?: string | null;
          isActiveIdentity: boolean;
          lastAppVersionName?: string | null;
          lastAppVersionCode?: number | null;
          lastAppVersionReportedAt?: Date | string | null;
        }>;
        meta: Array<{ total: number }>;
      }>(pipeline as unknown as PipelineStage[])
      .exec();

    const toIso = (v: Date | string | null | undefined): string | null => {
      if (v == null) return null;
      if (v instanceof Date) return v.toISOString();
      const trimmed = String(v).trim();
      return trimmed.length > 0 ? trimmed : null;
    };

    const total = facet?.meta?.[0]?.total ?? 0;
    const items: AdminUserOnServerRow[] = (facet?.data ?? []).map((row) => ({
      userId: row.userId,
      identityId: row.identityId,
      accountUsername: row.email,
      email: row.email,
      serverNumber: row.serverNumber,
      gameNickname: row.gameNickname,
      playerTeamId: row.playerTeamId,
      playerTeamTag: row.playerTeamTag ?? null,
      playerTeamDisplayName: row.playerTeamDisplayName ?? null,
      isActiveIdentity: row.isActiveIdentity,
      accountRole: normalizeAllianceRole(row.role),
      membershipStatus: row.membershipStatus ?? 'active',
      appVersionName: row.lastAppVersionName?.trim() || null,
      appVersionCode:
        typeof row.lastAppVersionCode === 'number' ? row.lastAppVersionCode : null,
      appVersionReportedAt: toIso(row.lastAppVersionReportedAt),
    }));

    return {
      items,
      total,
      skip,
      limit,
      hasMore: skip + items.length < total,
    };
  }

  async switchActive(
    userId: string,
    identityId: string,
  ): Promise<UserDocument> {
    if (!Types.ObjectId.isValid(identityId)) {
      throw new NotFoundException('Game identity not found');
    }
    const user = await this.userModel.findById(userId).exec();
    if (!user) throw new NotFoundException('User not found');
    const migrated = await this.ensureMigrated(user);
    const exists = (migrated.gameIdentities ?? []).some(
      (g) => this.identityId(g) === identityId,
    );
    if (!exists) throw new NotFoundException('Game identity not found');
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        { $set: { activeGameIdentityId: new Types.ObjectId(identityId) } },
        { returnDocument: 'after' },
      )
      .exec();
    if (!updated) throw new NotFoundException('User not found');
    return this.syncUserFromActiveIdentity(updated);
  }
}
