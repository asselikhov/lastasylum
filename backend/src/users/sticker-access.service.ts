import {
  BadRequestException,
  ForbiddenException,
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
import {
  isKnownStickerPackKey,
  KNOWN_STICKER_PACK_KEYS,
} from '../common/constants/sticker-packs';
import { stickerPackKeyFromStickerOnlyMessage } from '../chat/sticker-wire.util';
import { User, UserDocument } from './schemas/user.schema';
import { GameIdentitiesService } from './game-identities.service';
import {
  AllianceStickerRoleGrant,
  AllianceStickerRoleGrantDocument,
} from './schemas/alliance-sticker-role-grant.schema';
import {
  AllianceStickerUserGrant,
  AllianceStickerUserGrantDocument,
} from './schemas/alliance-sticker-user-grant.schema';

export type StickerPackCatalogEntry = {
  key: string;
  title: string;
};

export type StickerAllianceMember = {
  userId: string;
  username: string;
  accountRole: AllianceRole;
  serverNumber: number | null;
};

export type AllianceStickerAccessView = {
  catalog: StickerPackCatalogEntry[];
  roleGrants: Record<string, AllianceRole[]>;
  userGrants: Record<string, string[]>;
  members: StickerAllianceMember[];
};

const ZLOBYAKA_TITLE = 'Злобяка';
const CHUSHUY_TITLE = 'Дракончик Чушуй';
const SOIDOW_CAT_TITLE = 'Soidow cat';

const STICKER_PACK_TITLES: Record<string, string> = {
  zlobyaka: ZLOBYAKA_TITLE,
  chushuy: CHUSHUY_TITLE,
  soidow_cat: SOIDOW_CAT_TITLE,
};

@Injectable()
export class StickerAccessService implements OnModuleInit {
  private readonly logger = new Logger(StickerAccessService.name);

  constructor(
    @InjectModel(AllianceStickerRoleGrant.name)
    private readonly roleGrantModel: Model<AllianceStickerRoleGrantDocument>,
    @InjectModel(AllianceStickerUserGrant.name)
    private readonly userGrantModel: Model<AllianceStickerUserGrantDocument>,
    @InjectModel(User.name) private readonly userModel: Model<UserDocument>,
    private readonly gameIdentities: GameIdentitiesService,
  ) {}

  async onModuleInit(): Promise<void> {
    for (const { from, to } of LEGACY_ALLIANCE_ROLE_MIGRATION) {
      const res = await this.roleGrantModel
        .updateMany({ role: from }, { $set: { role: to } })
        .exec();
      if (res.modifiedCount > 0) {
        this.logger.log(
          `Migrated ${res.modifiedCount} sticker role grant(s) ${from} → ${to}`,
        );
      }
    }
  }

  catalog(): StickerPackCatalogEntry[] {
    return KNOWN_STICKER_PACK_KEYS.map((key) => ({
      key,
      title: STICKER_PACK_TITLES[key] ?? key,
    }));
  }

  async listEnabledPackKeysForUser(user: UserDocument): Promise<string[]> {
    const allianceName = user.allianceName?.trim();
    if (!allianceName) {
      return [];
    }
    const uid = user._id;
    const keys = new Set<string>();

    const roleRows = await this.roleGrantModel
      .find({ allianceName, role: normalizeAllianceRole(user.role) })
      .select('packKey')
      .lean<Array<{ packKey: string }>>()
      .exec();
    for (const r of roleRows) {
      if (isKnownStickerPackKey(r.packKey)) keys.add(r.packKey);
    }

    const userRows = await this.userGrantModel
      .find({ allianceName, userId: uid })
      .select('packKey')
      .lean<Array<{ packKey: string }>>()
      .exec();
    for (const r of userRows) {
      if (isKnownStickerPackKey(r.packKey)) keys.add(r.packKey);
    }

    return [...keys];
  }

  async assertUserMaySendStickerMessage(
    user: UserDocument,
    text: string,
  ): Promise<void> {
    const packKey = stickerPackKeyFromStickerOnlyMessage(text);
    if (!packKey) return;
    const allowed = await this.listEnabledPackKeysForUser(user);
    if (!allowed.includes(packKey)) {
      throw new ForbiddenException('STICKER_PACK_LOCKED');
    }
  }

  async getAllianceAccess(
    allianceName: string,
  ): Promise<AllianceStickerAccessView> {
    const trimmed = allianceName.trim();
    const roleDocs = await this.roleGrantModel
      .find({ allianceName: trimmed })
      .lean<Array<{ packKey: string; role: AllianceRole }>>()
      .exec();
    const userDocs = await this.userGrantModel
      .find({ allianceName: trimmed })
      .lean<Array<{ packKey: string; userId: Types.ObjectId }>>()
      .exec();

    const roleGrants: Record<string, AllianceRole[]> = {};
    for (const d of roleDocs) {
      if (!isKnownStickerPackKey(d.packKey)) continue;
      const list = roleGrants[d.packKey] ?? [];
      if (!list.includes(d.role)) list.push(d.role);
      roleGrants[d.packKey] = list;
    }
    for (const k of Object.keys(roleGrants)) {
      roleGrants[k].sort();
    }

    const userGrants: Record<string, string[]> = {};
    for (const d of userDocs) {
      if (!isKnownStickerPackKey(d.packKey)) continue;
      const id = d.userId.toString();
      const list = userGrants[d.packKey] ?? [];
      if (!list.includes(id)) list.push(id);
      userGrants[d.packKey] = list;
    }
    for (const k of Object.keys(userGrants)) {
      userGrants[k].sort();
    }

    const members = await this.listAllianceMembers(trimmed);

    return {
      catalog: this.catalog(),
      roleGrants,
      userGrants,
      members,
    };
  }

  private async listAllianceMembers(
    allianceName: string,
  ): Promise<StickerAllianceMember[]> {
    const users = await this.userModel
      .find({ allianceName })
      .sort({ membershipStatus: 1, role: 1, username: 1 })
      .exec();
    return users.map((u) => {
      const active = this.gameIdentities.getActiveIdentity(u);
      return {
        userId: u._id.toString(),
        username: u.username,
        accountRole: normalizeAllianceRole(u.role),
        serverNumber: active?.serverNumber ?? null,
      };
    });
  }

  async replaceUserPackGrants(
    allianceName: string,
    userId: string,
    packKeys: string[],
  ): Promise<AllianceStickerAccessView> {
    const trimmed = allianceName.trim();
    if (!trimmed) {
      throw new BadRequestException('allianceName is required');
    }
    if (!Types.ObjectId.isValid(userId)) {
      throw new BadRequestException('Invalid user id');
    }
    const user = await this.userModel.findById(userId).exec();
    if (!user || user.allianceName.trim() !== trimmed) {
      throw new BadRequestException(
        `User ${userId} is not in alliance ${trimmed}`,
      );
    }
    const uniqueKeys = [
      ...new Set(packKeys.map((k) => k.trim()).filter(Boolean)),
    ];
    for (const packKey of uniqueKeys) {
      if (!isKnownStickerPackKey(packKey)) {
        throw new BadRequestException(`Unknown sticker pack: ${packKey}`);
      }
    }
    const uid = new Types.ObjectId(userId);
    await this.userGrantModel
      .deleteMany({ allianceName: trimmed, userId: uid })
      .exec();
    if (uniqueKeys.length > 0) {
      await this.userGrantModel.insertMany(
        uniqueKeys.map((packKey) => ({
          allianceName: trimmed,
          packKey,
          userId: uid,
        })),
      );
    }
    return this.getAllianceAccess(trimmed);
  }

  async replaceAllianceAccess(
    allianceName: string,
    body: {
      roleGrants?: Record<string, AllianceRole[]>;
      userGrants?: Record<string, string[]>;
    },
  ): Promise<AllianceStickerAccessView> {
    const trimmed = allianceName.trim();
    if (!trimmed) {
      throw new BadRequestException('allianceName is required');
    }

    for (const packKey of Object.keys(body.roleGrants ?? {})) {
      if (!isKnownStickerPackKey(packKey)) {
        throw new BadRequestException(`Unknown sticker pack: ${packKey}`);
      }
    }
    for (const packKey of Object.keys(body.userGrants ?? {})) {
      if (!isKnownStickerPackKey(packKey)) {
        throw new BadRequestException(`Unknown sticker pack: ${packKey}`);
      }
    }

    await this.roleGrantModel.deleteMany({ allianceName: trimmed }).exec();
    await this.userGrantModel.deleteMany({ allianceName: trimmed }).exec();

    const roleBulk: Array<{
      allianceName: string;
      packKey: string;
      role: AllianceRole;
    }> = [];
    for (const [packKey, roles] of Object.entries(body.roleGrants ?? {})) {
      if (!isKnownStickerPackKey(packKey)) continue;
      for (const role of roles ?? []) {
        const norm = normalizeAllianceRole(role);
        if (!Object.values(AllianceRole).includes(norm)) {
          throw new BadRequestException(`Invalid alliance role: ${role}`);
        }
        roleBulk.push({ allianceName: trimmed, packKey, role: norm });
      }
    }
    if (roleBulk.length > 0) {
      await this.roleGrantModel.insertMany(roleBulk);
    }

    const userBulk: Array<{
      allianceName: string;
      packKey: string;
      userId: Types.ObjectId;
    }> = [];
    for (const [packKey, ids] of Object.entries(body.userGrants ?? {})) {
      if (!isKnownStickerPackKey(packKey)) continue;
      for (const raw of ids ?? []) {
        const id = raw.trim();
        if (!Types.ObjectId.isValid(id)) {
          throw new BadRequestException(`Invalid user id: ${raw}`);
        }
        userBulk.push({
          allianceName: trimmed,
          packKey,
          userId: new Types.ObjectId(id),
        });
      }
    }
    if (userBulk.length > 0) {
      await this.userGrantModel.insertMany(userBulk);
    }

    return this.getAllianceAccess(trimmed);
  }
}
