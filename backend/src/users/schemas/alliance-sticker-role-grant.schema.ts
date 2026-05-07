import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';
import { AllianceRole } from '../../common/enums/alliance-role.enum';

export type AllianceStickerRoleGrantDocument =
  HydratedDocument<AllianceStickerRoleGrant>;

@Schema({ timestamps: true })
export class AllianceStickerRoleGrant {
  @Prop({ required: true, trim: true })
  allianceName: string;

  @Prop({ required: true, trim: true })
  packKey: string;

  @Prop({ type: String, required: true, enum: AllianceRole })
  role: AllianceRole;
}

export const AllianceStickerRoleGrantSchema = SchemaFactory.createForClass(
  AllianceStickerRoleGrant,
);

AllianceStickerRoleGrantSchema.index(
  { allianceName: 1, packKey: 1, role: 1 },
  { unique: true },
);
