import {
  BadRequestException,
  ConflictException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import {
  GameIdentity,
  GameIdentityDocument,
} from './schemas/game-identity.schema';
import {
  PlayerTeam,
  PlayerTeamDocument,
} from './schemas/player-team.schema';
import { User, UserDocument } from './schemas/user.schema';

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
      throw new BadRequestException(
        'Game nickname must be 2–32 characters',
      );
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

  private identityId(identity: GameIdentity & { _id?: Types.ObjectId }): string {
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
    return active?.gameNickname?.trim() || user.username;
  }

  resolveMemberDisplayNickname(
    user: UserDocument,
    teamId: string,
  ): string {
    const teamOid = Types.ObjectId.isValid(teamId)
      ? new Types.ObjectId(teamId)
      : null;
    if (teamOid) {
      const match = (user.gameIdentities ?? []).find((g) =>
        g.playerTeamId?.equals(teamOid),
      );
      if (match?.gameNickname?.trim()) {
        return match.gameNickname.trim();
      }
    }
    return this.resolveSenderUsername(user);
  }

  async setPlayerTeamOnActive(
    userId: string,
    teamId: Types.ObjectId | null,
    teamTag: string | null,
    teamDisplayName: string | null,
  ): Promise<UserDocument | null> {
    const user = await this.userModel.findById(userId).exec();
    if (!user) return null;
    const migrated = await this.ensureMigrated(user);
    const active = this.getActiveIdentity(migrated);
    if (!active?._id) return migrated;
    const identities = (migrated.gameIdentities ?? []).map((g) => {
      const base = {
        _id: g._id,
        serverNumber: g.serverNumber,
        gameNickname: g.gameNickname,
        playerTeamId: g.playerTeamId ?? null,
      };
      if (this.identityId(g) !== active._id.toString()) {
        return base;
      }
      return { ...base, playerTeamId: teamId };
    });
    const updated = await this.userModel
      .findByIdAndUpdate(
        userId,
        {
          $set: {
            gameIdentities: identities,
            playerTeamId: teamId,
            teamTag,
            teamDisplayName,
          },
        },
        { returnDocument: 'after' },
      )
      .exec();
    return updated;
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
      throw new ConflictException(
        'You already have a nickname on this server',
      );
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
        (g) =>
          this.identityId(g) !== identityId && g.serverNumber === server,
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
    const wasActive =
      migrated.activeGameIdentityId?.toString() === identityId;
    const identities = (migrated.gameIdentities ?? []).filter(
      (g) => this.identityId(g) !== identityId,
    );
    const nextActiveId = wasActive
      ? identities[0]?._id ?? null
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
