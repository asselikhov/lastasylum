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
  isAppAdminRole,
  LEGACY_ALLIANCE_ROLE_MIGRATION,
  normalizeAllianceRole,
} from '../common/alliance-role.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import {
  isKnownStickerPackKey,
  KNOWN_STICKER_PACK_KEYS,
} from '../common/constants/sticker-packs';
import { stickerPackKeyFromStickerOnlyMessage } from '../chat/sticker-wire.util';
import { UserDocument } from './schemas/user.schema';
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

export type AllianceStickerAccessView = {
  catalog: StickerPackCatalogEntry[];
  roleGrants: Record<string, AllianceRole[]>;
  userGrants: Record<string, string[]>;
};

const ZLOBYAKA_TITLE = 'Злобяка';

@Injectable()
export class StickerAccessService implements OnModuleInit {
  private readonly logger = new Logger(StickerAccessService.name);

  constructor(
    @InjectModel(AllianceStickerRoleGrant.name)
    private readonly roleGrantModel: Model<AllianceStickerRoleGrantDocument>,
    @InjectModel(AllianceStickerUserGrant.name)
    private readonly userGrantModel: Model<AllianceStickerUserGrantDocument>,
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
      title: key === 'zlobyaka' ? ZLOBYAKA_TITLE : key,
    }));
  }

  /** App admins may use every built-in pack without explicit grants. */
  private isAllianceAdmin(user: UserDocument): boolean {
    return isAppAdminRole(user.role);
  }

  async listEnabledPackKeysForUser(user: UserDocument): Promise<string[]> {
    if (this.isAllianceAdmin(user)) {
      return [...KNOWN_STICKER_PACK_KEYS];
    }
    const allianceName = user.allianceName?.trim();
    if (!allianceName) {
      return [];
    }
    const uid = user._id as Types.ObjectId;
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
    if (this.isAllianceAdmin(user)) return;
    const allowed = await this.listEnabledPackKeysForUser(user);
    if (!allowed.includes(packKey)) {
      throw new ForbiddenException('STICKER_PACK_LOCKED');
    }
  }

  async getAllianceAccess(allianceName: string): Promise<AllianceStickerAccessView> {
    const trimmed = allianceName.trim();
    const roleDocs = await this.roleGrantModel
      .find({ allianceName: trimmed })
      .lean<
        Array<{ packKey: string; role: AllianceRole }>
      >()
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

    return {
      catalog: this.catalog(),
      roleGrants,
      userGrants,
    };
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
