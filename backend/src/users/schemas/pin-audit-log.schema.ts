import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';

export type PinAuditLogDocument = HydratedDocument<PinAuditLog>;

export type PinAuditScope = 'chat' | 'forum';
export type PinAuditAction = 'pin' | 'unpin' | 'unpin_all';

@Schema({ collection: 'pin_audit_logs', timestamps: { createdAt: true, updatedAt: false } })
export class PinAuditLog {
  @Prop({ type: Types.ObjectId, required: true, index: true })
  teamId: Types.ObjectId;

  @Prop({ type: String, required: true, enum: ['chat', 'forum'] })
  scope!: PinAuditScope;

  /** Chat room id or forum topic id. */
  @Prop({ type: String, required: true })
  scopeId!: string;

  @Prop({ type: String, default: null })
  messageId!: string | null;

  @Prop({ type: String, required: true, enum: ['pin', 'unpin', 'unpin_all'] })
  action!: PinAuditAction;

  @Prop({ type: String, required: true })
  userId: string;

  createdAt?: Date;
}

export const PinAuditLogSchema = SchemaFactory.createForClass(PinAuditLog);
PinAuditLogSchema.index({ teamId: 1, createdAt: -1 });
