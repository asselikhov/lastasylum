import {
  BadRequestException,
  ConflictException,
  Injectable,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { AllianceRegistryService } from './alliance-registry.service';
import { User, UserDocument } from './schemas/user.schema';
import { TeamsService } from './teams.service';

export type SafeUser = {
  id: string;
  username: string;
  email: string;
  role: AllianceRole;
  allianceName: string;
  alliancePublicId: string;
  overlayTabVisible: boolean;
  teamDisplayName: string | null;
  teamTag: string | null;
  membershipStatus: TeamMembershipStatus;
  presenceStatus: string | null;
  lastPresenceAt: string | null;
  telegramUsername: string | null;
  playerTeamId: string | null;
  playerTeamTag: string | null;
  playerTeamDisplayName: string | null;
  playerTeamLeaderUserId: string | null;
  isPlayerTeamLeader: boolean;
  pendingPlayerTeamJoinRequests: number;
};

@Injectable()
export class UsersService {
  constructor(
    @InjectModel(User.name) private readonly userModel: Model<User>,
    private readonly allianceRegistry: AllianceRegistryService,
    private readonly teamsService: TeamsService,
  ) {}

  effectiveMembership(user: UserDocument): TeamMembershipStatus {
    return user.membershipStatus ?? TeamMembershipStatus.ACTIVE;
  }

  async findByEmail(email: string): Promise<UserDocument | null> {
    return this.userModel.findOne({ email: email.toLowerCase() }).exec();
  }

  async createUser(input: {
    username: string;
    email: string;
    passwordHash: string;
    role?: AllianceRole;
  }): Promise<UserDocument> {
    return this.userModel.create({
      username: input.username,
      email: input.email.toLowerCase(),
      passwordHash: input.passwordHash,
      role: input.role ?? AllianceRole.R2,
      membershipStatus: TeamMembershipStatus.PENDING,
    });
  }

  async findById(id: string): Promise<UserDocument | null> {
    return this.userModel.findById(id).exec();
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
    q?: string;
    skip: number;
    limit: number;
  }): Promise<UserDocument[]> {
    const filter: Record<string, unknown> = {};
    if (opts.allianceCode?.trim()) {
      filter.allianceName = opts.allianceCode.trim();
    }
    if (opts.q?.trim()) {
      const esc = opts.q.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      filter.$or = [
        { username: new RegExp(esc, 'i') },
        { email: new RegExp(esc, 'i') },
      ];
    }
    return this.userModel
      .find(filter)
      .sort({ allianceName: 1, membershipStatus: 1, role: 1, username: 1 })
      .skip(opts.skip)
      .limit(opts.limit)
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
    return this.userModel
      .findByIdAndUpdate(userId, { role }, { new: true })
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
      .findByIdAndUpdate(userId, update, { new: true })
      .exec();
  }

  async updateUsername(
    userId: string,
    username: string,
  ): Promise<UserDocument | null> {
    const trimmed = username.trim();
    const taken = await this.userModel
      .findOne({
        username: trimmed,
        _id: { $ne: userId },
      })
      .exec();
    if (taken) {
      throw new ConflictException('Username is already taken');
    }
    return this.userModel
      .findByIdAndUpdate(userId, { username: trimmed }, { new: true })
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
      .findByIdAndUpdate(userId, { mutedUntil }, { new: true })
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
        { new: true },
      )
      .exec();
  }

  async toSafeUser(user: UserDocument): Promise<SafeUser> {
    const flags = await this.allianceRegistry.resolveFlagsByAllianceCode(
      user.allianceName,
    );
    const teamFields = await this.teamsService.getPlayerTeamProfileFields(user);
    const inSquad = Boolean(teamFields.playerTeamId);
    return {
      id: user._id.toString(),
      username: user.username,
      email: user.email,
      role: user.role,
      allianceName: user.allianceName,
      alliancePublicId: flags.alliancePublicId,
      overlayTabVisible: flags.overlayTabVisible,
      teamDisplayName: inSquad
        ? teamFields.playerTeamDisplayName
        : (user.teamDisplayName ?? null),
      teamTag: inSquad ? teamFields.playerTeamTag : (user.teamTag ?? null),
      membershipStatus: this.effectiveMembership(user),
      presenceStatus: user.presenceStatus ?? null,
      lastPresenceAt: user.lastPresenceAt
        ? user.lastPresenceAt.toISOString()
        : null,
      telegramUsername: user.telegramUsername ?? null,
      ...teamFields,
    };
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
        { new: true },
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

  async updatePresence(userId: string, status: string): Promise<void> {
    await this.userModel
      .updateOne(
        { _id: userId },
        {
          $set: {
            presenceStatus: status.trim().slice(0, 32),
            lastPresenceAt: new Date(),
          },
        },
      )
      .exec();
  }

  async collectPushTokensForAlliance(
    allianceName: string,
    excludeUserId: string,
  ): Promise<string[]> {
    if (!Types.ObjectId.isValid(excludeUserId)) return [];
    const users = await this.userModel
      .find({
        allianceName,
        membershipStatus: TeamMembershipStatus.ACTIVE,
        _id: { $ne: new Types.ObjectId(excludeUserId) },
        pushFcmTokens: { $exists: true, $ne: [] },
      })
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
