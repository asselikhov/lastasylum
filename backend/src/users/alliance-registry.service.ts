import { Injectable, NotFoundException } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { randomBytes } from 'node:crypto';
import { Model } from 'mongoose';
import { User } from './schemas/user.schema';
import {
  AllianceRegistry,
  AllianceRegistryDocument,
} from './schemas/alliance-registry.schema';

export type AllianceAdminRow = {
  allianceCode: string;
  publicId: string;
  overlayEnabled: boolean;
  memberCount: number;
};

@Injectable()
export class AllianceRegistryService {
  constructor(
    @InjectModel(AllianceRegistry.name)
    private readonly registryModel: Model<AllianceRegistryDocument>,
    @InjectModel(User.name) private readonly userModel: Model<User>,
  ) {}

  private newPublicId(): string {
    return randomBytes(8).toString('base64url').replace(/=/g, '').slice(0, 12);
  }

  async ensureRowForAllianceCode(
    allianceCode: string,
  ): Promise<AllianceRegistryDocument> {
    const code = allianceCode.trim();
    const existing = await this.registryModel.findOne({ allianceCode: code }).exec();
    if (existing) {
      return existing;
    }
    for (let i = 0; i < 24; i += 1) {
      const publicId = this.newPublicId();
      try {
        return await this.registryModel.create({
          allianceCode: code,
          publicId,
          overlayEnabled: false,
        });
      } catch {
        // duplicate publicId — retry
      }
    }
    throw new Error('Failed to allocate unique alliance publicId');
  }

  async resolveFlagsByAllianceCode(allianceCode: string): Promise<{
    alliancePublicId: string;
    overlayTabVisible: boolean;
  }> {
    const row = await this.ensureRowForAllianceCode(allianceCode);
    return {
      alliancePublicId: row.publicId,
      overlayTabVisible: row.overlayEnabled === true,
    };
  }

  async listForAdmin(): Promise<AllianceAdminRow[]> {
    const distinctCodes = await this.userModel.distinct<string>('allianceName');
    for (const c of distinctCodes) {
      if (typeof c === 'string' && c.length > 0) {
        await this.ensureRowForAllianceCode(c);
      }
    }
    const rows = await this.registryModel
      .find()
      .sort({ allianceCode: 1 })
      .lean<
        Array<{
          allianceCode: string;
          publicId: string;
          overlayEnabled: boolean;
        }>
      >()
      .exec();
    const agg = await this.userModel
      .aggregate<{ _id: string; c: number }>([
        { $group: { _id: '$allianceName', c: { $sum: 1 } } },
      ])
      .exec();
    const counts = new Map<string, number>();
    for (const a of agg) {
      counts.set(a._id, a.c);
    }
    return rows.map((r) => ({
      allianceCode: r.allianceCode,
      publicId: r.publicId,
      overlayEnabled: r.overlayEnabled,
      memberCount: counts.get(r.allianceCode) ?? 0,
    }));
  }

  async updateOverlayByPublicId(
    publicId: string,
    overlayEnabled: boolean,
  ): Promise<AllianceAdminRow> {
    const trimmed = publicId.trim();
    const updated = await this.registryModel
      .findOneAndUpdate(
        { publicId: trimmed },
        { $set: { overlayEnabled } },
        { new: true },
      )
      .lean<{
        allianceCode: string;
        publicId: string;
        overlayEnabled: boolean;
      } | null>()
      .exec();
    if (!updated) {
      throw new NotFoundException('Alliance not found');
    }
    const memberCount = await this.userModel.countDocuments({
      allianceName: updated.allianceCode,
    });
    return {
      allianceCode: updated.allianceCode,
      publicId: updated.publicId,
      overlayEnabled: updated.overlayEnabled,
      memberCount,
    };
  }
}
