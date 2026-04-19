import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';

export type AllianceRegistryDocument = HydratedDocument<AllianceRegistry>;

/**
 * One row per alliance code (same value as User.allianceName).
 * [publicId] is the stable external team id; [overlayEnabled] is set by R5 admin.
 */
@Schema({ timestamps: true, collection: 'allianceregistries' })
export class AllianceRegistry {
  @Prop({ required: true, unique: true, trim: true })
  allianceCode: string;

  @Prop({ required: true, unique: true, trim: true })
  publicId: string;

  @Prop({ required: true, default: false })
  overlayEnabled: boolean;
}

export const AllianceRegistrySchema =
  SchemaFactory.createForClass(AllianceRegistry);
