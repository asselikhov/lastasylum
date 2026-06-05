import { Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import {
  PinAuditAction,
  PinAuditLog,
  PinAuditLogDocument,
  PinAuditScope,
} from './schemas/pin-audit-log.schema';

export interface PinAuditRow {
  id: string;
  teamId: string;
  scope: PinAuditScope;
  scopeId: string;
  messageId: string | null;
  action: PinAuditAction;
  userId: string;
  createdAt: string;
}

@Injectable()
export class PinAuditService {
  constructor(
    @InjectModel(PinAuditLog.name)
    private readonly auditModel: Model<PinAuditLogDocument>,
  ) {}

  async append(input: {
    teamId: string;
    scope: PinAuditScope;
    scopeId: string;
    messageId: string | null;
    action: PinAuditAction;
    userId: string;
  }): Promise<void> {
    const teamId = input.teamId?.trim() ?? '';
    const scopeId = input.scopeId?.trim() ?? '';
    const userId = input.userId?.trim() ?? '';
    if (!Types.ObjectId.isValid(teamId) || !scopeId || !userId) return;
    await this.auditModel.create({
      teamId: new Types.ObjectId(teamId),
      scope: input.scope,
      scopeId,
      messageId: input.messageId?.trim() || null,
      action: input.action,
      userId,
    });
  }

  async listForTeam(teamId: string, limitRaw = 50): Promise<PinAuditRow[]> {
    const teamOid = teamId?.trim() ?? '';
    if (!Types.ObjectId.isValid(teamOid)) return [];
    const limit = Math.min(200, Math.max(1, limitRaw));
    const rows = await this.auditModel
      .find({ teamId: new Types.ObjectId(teamOid) })
      .sort({ createdAt: -1 })
      .limit(limit)
      .lean()
      .exec();
    return rows.map((row) => ({
      id: String(row._id),
      teamId: String(row.teamId),
      scope: row.scope as PinAuditScope,
      scopeId: row.scopeId,
      messageId: row.messageId ?? null,
      action: row.action as PinAuditAction,
      userId: row.userId,
      createdAt:
        row.createdAt instanceof Date
          ? row.createdAt.toISOString()
          : new Date().toISOString(),
    }));
  }
}
