import {
  BadRequestException,
  ConflictException,
  Injectable,
  Logger,
  OnModuleInit,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import {
  LEGACY_ALLIANCE_ROLE_MIGRATION,
  normalizeAllianceRole,
} from '../common/alliance-role.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { AllianceRegistryService } from './alliance-registry.service';
import { User, UserDocument } from './schemas/user.schema';
import { GameIdentitiesService, SafeGameIdentity } from './game-identities.service';
import { StickerAccessService } from './sticker-access.service';
import { TeamsService } from './teams.service';

export type SafeUser = {
  id: string;
  username: string;
  email: string;
  /** App account role (MEMBER…ADMIN), not squad rank R1–R5. */
  role: AllianceRole;
  allianceName: string;
  alliancePublicId: string;
  overlayTabVisible: boolean;
  teamDisplayName: string | null;
  teamTag: string | null;
  membershipStatus: TeamMembershipStatus;
  presenceStatus: string | null;
  lastPresenceAt: string | null;
  lastAppActiveAt: string | null;
  telegramUsername: string | null;
  playerTeamId: string | null;
  playerTeamTag: string | null;
  playerTeamDisplayName: string | null;
  playerTeamLeaderUserId: string | null;
  isPlayerTeamLeader: boolean;
  pendingPlayerTeamJoinRequests: number;
  /** Squad rank on the player team (R1–R5), not app alliance role. */
  playerTeamSquadRole: string | null;
  /** Alliance sticker packs this account may send (wire keys, e.g. zlobyaka). */
  enabledStickerPacks: string[];
  excavationPushEnabled: boolean;
  /** True when at least one FCM device token is stored (push can be delivered). */
  pushNotificationsRegistered: boolean;
  gameIdentities: SafeGameIdentity[];
  activeGameIdentityId: string | null;
  activeGameNickname: string | null;
  activeServerNumber: number | null;
};

@Injectable()
export class UsersService implements OnModuleInit {
  private readonly logger = new Logger(UsersService.name);

  constructor(
    @InjectModel(User.name) private readonly userModel: Model<User>,
    private readonly allianceRegistry: AllianceRegistryService,
    private readonly teamsService: TeamsService,
    private readonly stickerAccess: StickerAccessService,
    private readonly gameIdentities: GameIdentitiesService,
  ) {}

  async onModuleInit(): Promise<void> {
    await this.migrateLegacyAllianceRoles();
  }

  private async migrateLegacyAllianceRoles(): Promise<void> {
    for (const { from, to } of LEGACY_ALLIANCE_ROLE_MIGRATION) {
      const res = await this.userModel
        .updateMany({ role: from }, { $set: { role: to } })
        .exec();
      if (res.modifiedCount > 0) {
        this.logger.log(
          `Migrated ${res.modifiedCount} user(s) alliance role ${from} → ${to}`,
        );
      }
    }
  }

  effectiveMembership(user: UserDocument): TeamMembershipStatus {
    return user.membershipStatus ?? TeamMembershipStatus.ACTIVE;
  }

  async findByEmail(email: string): Promise<UserDocument | null> {
    return this.userModel.findOne({ email: email.toLowerCase() }).exec();
  }

  /** New accounts are always regular members; app admin (AllianceRole.ADMIN) is assigned manually. */
  async createUser(input: {
    username: string;
    email: string;
    passwordHash: string;
    serverNumber?: number;
    gameNickname?: string;
  }): Promise<UserDocument> {
    const user = await this.userModel.create({
      username: input.username,
      email: input.email.toLowerCase(),
      passwordHash: input.passwordHash,
      role: AllianceRole.MEMBER,
      membershipStatus: TeamMembershipStatus.ACTIVE,
    });
    if (input.serverNumber != null && input.gameNickname != null) {
      return this.gameIdentities.createInitialIdentity(
        user,
        input.serverNumber,
        input.gameNickname,
      );
    }
    return this.gameIdentities.ensureMigrated(user);
  }

  async findById(id: string): Promise<UserDocument | null> {
    const user = await this.userModel.findById(id).exec();
    if (!user) {
      return null;
    }
    return this.teamsService.reconcileSquadTeamBindingForUser(id);
  }

  async findByUsername(username: string): Promise<UserDocument | null> {
    return this.userModel.findOne({ username: username.trim() }).exec();
  }

  async listByAlliance(allianceName: string): Promise<UserDocument[]> {
    return this.userModel
      .find({ allianceName })
      .sort({ membershipStatus: 1, role: 1, username: 1 })
      .exec();
  }

  /** R5 admin: optional alliance filter, optional text search, pagination. */
  async listUsersForAdmin(opts: {
    allianceCode?: string;
    withoutTeam?: boolean;
    q?: string;
    skip: number;
    limit: number;
  }): Promise<UserDocument[]> {
    const filter: Record<string, unknown> = {};
    const and: Record<string, unknown>[] = [];
    if (opts.withoutTeam) {
      and.push({
        $or: [{ playerTeamId: null }, { playerTeamId: { $exists: false } }],
      });
    }
    if (opts.allianceCode?.trim()) {
      filter.allianceName = opts.allianceCode.trim();
    }
    if (opts.q?.trim()) {
      const esc = opts.q.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      and.push({
        $or: [
          { username: new RegExp(esc, 'i') },
          { email: new RegExp(esc, 'i') },
        ],
      });
    }
    if (and.length === 1) {
      Object.assign(filter, and[0]);
    } else if (and.length > 1) {
      filter.$and = and;
    }
    return this.userModel
      .find(filter)
      .sort({ allianceName: 1, membershipStatus: 1, role: 1, username: 1 })
      .skip(opts.skip)
      .limit(opts.limit)
      .exec();
  }

  async listUsersWithoutTeamForAdmin(opts: {
    q?: string;
    skip: number;
    limit: number;
  }): Promise<UserDocument[]> {
    return this.listUsersForAdmin({ ...opts, withoutTeam: true });
  }

  async countUsersWithoutTeam(): Promise<number> {
    return this.userModel
      .countDocuments({
        $or: [{ playerTeamId: null }, { playerTeamId: { $exists: false } }],
      })
      .exec();
  }

  /**
   * Label for the alliance-only chat room: latest active member's team display name, else alliance key.
   */
  async resolveAllianceChatHubTitle(allianceName: string): Promise<string> {
    const docs = await this.userModel
      .find({
        allianceName,
        membershipStatus: TeamMembershipStatus.ACTIVE,
      })
      .sort({ updatedAt: -1 })
      .limit(40)
      .select('teamDisplayName')
      .lean<Array<{ teamDisplayName?: string | null }>>()
      .exec();
    for (const d of docs) {
      const t = d.teamDisplayName?.trim();
      if (t && t.length > 0) {
        return t.slice(0, 64);
      }
    }
    return allianceName;
  }

  async updateRole(
    userId: string,
    role: AllianceRole,
  ): Promise<UserDocument | null> {
    const normalized = normalizeAllianceRole(role);
    return this.userModel
      .findByIdAndUpdate(userId, { role: normalized }, { returnDocument: 'after' })
      .exec();
  }

  async updateMembershipStatus(
    userId: string,
    status: TeamMembershipStatus,
  ): Promise<UserDocument | null> {
    const update: Partial<User> = { membershipStatus: status };
    if (status === TeamMembershipStatus.REMOVED) {
      update.refreshTokenHash = null;
    }
    return this.userModel
      .findByIdAndUpdate(userId, update, { returnDocument: 'after' })
      .exec();
  }

  /** Account login is the email; username is kept in sync for legacy fields. */
  async updateUsername(
    userId: string,
    username: string,
  ): Promise<UserDocument | null> {
    const email = username.trim().toLowerCase();
    const taken = await this.userModel
      .findOne({
        email,
        _id: { $ne: userId },
      })
      .exec();
    if (taken) {
      throw new ConflictException('Email is already in use');
    }
    return this.userModel
      .findByIdAndUpdate(
        userId,
        { email, username: email },
        { returnDocument: 'after' },
      )
      .exec();
  }

  async deleteUser(userId: string): Promise<boolean> {
    const res = await this.userModel.deleteOne({ _id: userId }).exec();
    return res.deletedCount === 1;
  }

  async updateRefreshTokenHash(
    userId: string,
    refreshTokenHash: string | null,
  ): Promise<void> {
    await this.userModel.findByIdAndUpdate(userId, { refreshTokenHash }).exec();
  }

  async muteUser(
    userId: string,
    mutedUntil: Date | null,
  ): Promise<UserDocument | null> {
    return this.userModel
      .findByIdAndUpdate(userId, { mutedUntil }, { returnDocument: 'after' })
      .exec();
  }

  async setPasswordResetToken(
    email: string,
    tokenHash: string,
    expires: Date,
  ): Promise<void> {
    await this.userModel
      .updateOne(
        { email: email.toLowerCase() },
        {
          $set: {
            passwordResetTokenHash: tokenHash,
            passwordResetExpires: expires,
          },
        },
      )
      .exec();
  }

  async applyPasswordReset(
    email: string,
    newPasswordHash: string,
  ): Promise<UserDocument | null> {
    return this.userModel
      .findOneAndUpdate(
        { email: email.toLowerCase() },
        {
          $set: {
            passwordHash: newPasswordHash,
            refreshTokenHash: null,
          },
          $unset: {
            passwordResetTokenHash: 1,
            passwordResetExpires: 1,
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
  }

  async toSafeUser(user: UserDocument): Promise<SafeUser> {
    const synced = await this.gameIdentities.ensureMigrated(user);
    const reconciled =
      (await this.teamsService.reconcileSquadTeamBindingForUser(
        synced._id.toString(),
      )) ?? synced;
    const flags = await this.allianceRegistry.resolveFlagsByAllianceCode(
      reconciled.allianceName,
    );
    const teamFields =
      await this.teamsService.getPlayerTeamProfileFields(reconciled);
    const gameIdentities =
      await this.gameIdentities.buildSafeIdentities(reconciled);
    const active = this.gameIdentities.getActiveIdentity(reconciled);
    const inSquad = Boolean(teamFields.playerTeamId);
    const enabledStickerPacks =
      await this.stickerAccess.listEnabledPackKeysForUser(reconciled);
    return {
      id: reconciled._id.toString(),
      username: reconciled.username,
      email: reconciled.email,
      role: normalizeAllianceRole(reconciled.role),
      allianceName: reconciled.allianceName,
      alliancePublicId: flags.alliancePublicId,
      overlayTabVisible: flags.overlayTabVisible,
      teamDisplayName: inSquad
        ? teamFields.playerTeamDisplayName
        : (reconciled.teamDisplayName ?? null),
      teamTag: inSquad ? teamFields.playerTeamTag : (reconciled.teamTag ?? null),
      membershipStatus: this.effectiveMembership(reconciled),
      presenceStatus: reconciled.presenceStatus ?? null,
      lastPresenceAt: reconciled.lastPresenceAt
        ? reconciled.lastPresenceAt.toISOString()
        : null,
      lastAppActiveAt: reconciled.lastAppActiveAt
        ? reconciled.lastAppActiveAt.toISOString()
        : null,
      telegramUsername: reconciled.telegramUsername ?? null,
      ...teamFields,
      enabledStickerPacks,
      excavationPushEnabled: reconciled.excavationPushEnabled !== false,
      pushNotificationsRegistered: (reconciled.pushFcmTokens?.length ?? 0) > 0,
      gameIdentities,
      activeGameIdentityId: active?._id?.toString() ?? null,
      activeGameNickname: active?.gameNickname ?? null,
      activeServerNumber: active?.serverNumber ?? null,
    };
  }

  async updateNotificationPreferences(
    userId: string,
    excavationPushEnabled: boolean,
  ): Promise<UserDocument | null> {
    return this.userModel
      .findByIdAndUpdate(
        userId,
        { $set: { excavationPushEnabled } },
        { returnDocument: 'after' },
      )
      .exec();
  }

  private normalizeTelegramUsername(
    raw: string | null | undefined,
  ): string | null {
    if (raw == null) return null;
    const trimmed = raw.trim();
    if (!trimmed) return null;
    const withoutAt = trimmed.startsWith('@') ? trimmed.slice(1) : trimmed;
    const lower = withoutAt.toLowerCase();
    if (!/^[a-z0-9_]{5,32}$/.test(lower)) {
      throw new BadRequestException('Invalid Telegram username');
    }
    return lower;
  }

  async updateMyTelegramUsername(
    userId: string,
    rawUsername: string | undefined,
  ): Promise<UserDocument | null> {
    if (rawUsername === undefined) {
      return this.findById(userId);
    }
    const normalized = this.normalizeTelegramUsername(rawUsername);
    return this.userModel
      .findByIdAndUpdate(
        userId,
        { $set: { telegramUsername: normalized } },
        { returnDocument: 'after' },
      )
      .exec();
  }

  async findTelegramUsernamesByIds(
    ids: string[],
  ): Promise<Map<string, string | null>> {
    const unique = [...new Set(ids.filter((id) => Types.ObjectId.isValid(id)))];
    if (!unique.length) {
      return new Map();
    }
    const docs = await this.userModel
      .find({ _id: { $in: unique.map((id) => new Types.ObjectId(id)) } })
      .select('_id telegramUsername')
      .lean<
        Array<{
          _id: Types.ObjectId;
          telegramUsername?: string | null;
        }>
      >()
      .exec();
    const map = new Map<string, string | null>();
    for (const d of docs) {
      map.set(d._id.toString(), d.telegramUsername ?? null);
    }
    return map;
  }

  async findTeamTagsByIds(ids: string[]): Promise<Map<string, string | null>> {
    const unique = [...new Set(ids.filter((id) => Types.ObjectId.isValid(id)))];
    if (!unique.length) {
      return new Map();
    }
    const docs = await this.userModel
      .find({ _id: { $in: unique.map((id) => new Types.ObjectId(id)) } })
      .select('_id teamTag')
      .lean<Array<{ _id: Types.ObjectId; teamTag?: string | null }>>()
      .exec();
    const map = new Map<string, string | null>();
    for (const d of docs) {
      map.set(d._id.toString(), d.teamTag ?? null);
    }
    return map;
  }

  async registerPushToken(userId: string, token: string): Promise<void> {
    const user = await this.findById(userId);
    if (!user) return;
    const prev = user.pushFcmTokens ?? [];
    const merged = [...new Set([...prev, token.trim()])].slice(-10);
    await this.userModel
      .updateOne({ _id: userId }, { $set: { pushFcmTokens: merged } })
      .exec();
  }

  async clearPushTokens(userId: string): Promise<void> {
    await this.userModel
      .updateOne({ _id: userId }, { $set: { pushFcmTokens: [] } })
      .exec();
  }

  async touchAppActivity(userId: string): Promise<void> {
    await this.userModel
      .updateOne({ _id: userId }, { $set: { lastAppActiveAt: new Date() } })
      .exec();
  }

  async updatePresence(userId: string, status: string): Promise<void> {
    const normalized = status.trim().toLowerCase().slice(0, 32);
    const now = new Date();
    if (normalized === 'ingame') {
      await this.userModel
        .updateOne(
          { _id: userId },
          {
            $set: {
              presenceStatus: 'ingame',
              lastPresenceAt: now,
            },
          },
        )
        .exec();
      return;
    }
    // Main app pings "online" while overlay service still runs in the game — keep ingame for allies.
    if (normalized === 'online') {
      const row = await this.userModel
        .findById(userId)
        .select('presenceStatus lastPresenceAt')
        .lean<{ presenceStatus?: string; lastPresenceAt?: Date | null }>()
        .exec();
      const lastAt = row?.lastPresenceAt;
      if (
        row?.presenceStatus === 'ingame' &&
        lastAt instanceof Date &&
        lastAt.getTime() >=
          Date.now() - UsersService.OVERLAY_INGAME_LIST_STALE_MS
      ) {
        await this.userModel
          .updateOne({ _id: userId }, { $set: { lastAppActiveAt: now } })
          .exec();
        return;
      }
    }
    await this.userModel
      .updateOne(
        { _id: userId },
        {
          $set: {
            presenceStatus: normalized,
            lastAppActiveAt: now,
          },
        },
      )
      .exec();
  }

  /** Push alerts: treat overlay as inactive after ~90s without ingame ping. */
  private static readonly OVERLAY_INGAME_STALE_MS = 90_000;

  /**
   * «Участники онлайн» / broadcast reactions — matches Android
   * [OVERLAY_INGAME_PRESENCE_STALE_MS] (~1.5× overlay heartbeat 60 s).
   */
  static readonly OVERLAY_INGAME_LIST_STALE_MS = 90_000;

  /** Fresh overlay «ingame» ping (same window as list/broadcast). */
  async isOverlayIngameNow(userId: string): Promise<boolean> {
    if (!Types.ObjectId.isValid(userId)) {
      return false;
    }
    const staleBefore = new Date(
      Date.now() - UsersService.OVERLAY_INGAME_LIST_STALE_MS,
    );
    const row = await this.userModel
      .findById(userId)
      .select('presenceStatus lastPresenceAt membershipStatus')
      .lean<{
        presenceStatus?: string | null;
        lastPresenceAt?: Date | null;
        membershipStatus?: string;
      }>()
      .exec();
    if (
      !row ||
      this.effectiveMembership(row as UserDocument) !==
        TeamMembershipStatus.ACTIVE
    ) {
      return false;
    }
    if (row.presenceStatus !== 'ingame') {
      return false;
    }
    const lastAt = row.lastPresenceAt;
    return lastAt instanceof Date && lastAt.getTime() >= staleBefore.getTime();
  }

  /**
   * Teammates currently in game with a fresh overlay heartbeat (excluding sender).
   */
  async listOverlayIngameTeammateIds(excludeUserId: string): Promise<string[]> {
    if (!Types.ObjectId.isValid(excludeUserId)) {
      return [];
    }
    const sender = await this.userModel
      .findById(excludeUserId)
      .select('playerTeamId membershipStatus')
      .lean<{ playerTeamId?: Types.ObjectId | null; membershipStatus?: string }>()
      .exec();
    if (
      !sender?.playerTeamId ||
      this.effectiveMembership(sender as UserDocument) !==
        TeamMembershipStatus.ACTIVE
    ) {
      return [];
    }
    const staleBefore = new Date(
      Date.now() - UsersService.OVERLAY_INGAME_LIST_STALE_MS,
    );
    const excludeOid = new Types.ObjectId(excludeUserId);
    const rows = await this.userModel
      .find({
        playerTeamId: sender.playerTeamId,
        _id: { $ne: excludeOid },
        membershipStatus: TeamMembershipStatus.ACTIVE,
        presenceStatus: 'ingame',
        lastPresenceAt: { $gte: staleBefore },
      })
      .select('_id')
      .lean<Array<{ _id: Types.ObjectId }>>()
      .exec();
    return rows.map((r) => r._id.toString());
  }

  async collectPushTokensForExcavationAlert(
    allianceId: string,
    excludeUserId: string,
  ): Promise<string[]> {
    if (!Types.ObjectId.isValid(excludeUserId)) return [];
    const filter: Record<string, unknown> = {
      membershipStatus: TeamMembershipStatus.ACTIVE,
      _id: { $ne: new Types.ObjectId(excludeUserId) },
      pushFcmTokens: { $exists: true, $ne: [] },
      excavationPushEnabled: { $ne: false },
    };
    if (allianceId.startsWith('pt:')) {
      const teamId = allianceId.slice(3);
      if (!Types.ObjectId.isValid(teamId)) return [];
      const teamOid = new Types.ObjectId(teamId);
      filter.$or = [
        { playerTeamId: teamOid },
        { 'gameIdentities.playerTeamId': teamOid },
      ];
    } else {
      filter.allianceName = allianceId;
    }
    const users = await this.userModel
      .find(filter)
      .select('_id pushFcmTokens')
      .lean<Array<{ _id: Types.ObjectId; pushFcmTokens?: string[] }>>()
      .exec();
    const out: string[] = [];
    let excludedOverlayIngame = 0;
    for (const u of users) {
      const userId = u._id.toString();
      if (await this.isOverlayIngameNow(userId)) {
        excludedOverlayIngame++;
        continue;
      }
      const arr = u.pushFcmTokens;
      if (Array.isArray(arr)) out.push(...arr);
    }
    if (excludedOverlayIngame > 0) {
      this.logger.debug(
        `FCM excavation: excluded ${excludedOverlayIngame} overlay-ingame ally(ies) (allianceId=${allianceId})`,
      );
    }
    return out;
  }

  async removeInvalidPushTokens(tokens: string[]): Promise<void> {
    const unique = [...new Set(tokens.filter((t) => t.trim().length > 0))];
    if (unique.length === 0) return;
    await this.userModel
      .updateMany(
        { pushFcmTokens: { $in: unique } },
        { $pull: { pushFcmTokens: { $in: unique } } },
      )
      .exec();
  }

  async collectPushTokensForAlliance(
    allianceId: string,
    excludeUserId: string,
  ): Promise<string[]> {
    if (!Types.ObjectId.isValid(excludeUserId)) return [];
    const filter: Record<string, unknown> = {
      membershipStatus: TeamMembershipStatus.ACTIVE,
      _id: { $ne: new Types.ObjectId(excludeUserId) },
      pushFcmTokens: { $exists: true, $ne: [] },
    };
    if (allianceId.startsWith('pt:')) {
      const teamId = allianceId.slice(3);
      if (!Types.ObjectId.isValid(teamId)) return [];
      filter.playerTeamId = new Types.ObjectId(teamId);
    } else {
      filter.allianceName = allianceId;
    }
    const users = await this.userModel
      .find(filter)
      .select('pushFcmTokens')
      .lean()
      .exec();
    const out: string[] = [];
    for (const u of users) {
      const arr = (u as { pushFcmTokens?: string[] }).pushFcmTokens;
      if (Array.isArray(arr)) out.push(...arr);
    }
    return out;
  }
}
