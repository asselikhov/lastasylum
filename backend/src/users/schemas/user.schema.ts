import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';
import { AllianceRole } from '../../common/enums/alliance-role.enum';

export type UserDocument = HydratedDocument<User>;

@Schema({ timestamps: true })
export class User {
  @Prop({ required: true, unique: true, trim: true })
  username: string;

  @Prop({ required: true, unique: true, lowercase: true, trim: true })
  email: string;

  @Prop({ required: true })
  passwordHash: string;

  @Prop({
    type: String,
    required: true,
    default: AllianceRole.R2,
    enum: AllianceRole,
  })
  role: AllianceRole;

  @Prop({ required: true, default: 'OBZHORY' })
  allianceName: string;

  @Prop({ type: String, default: null })
  refreshTokenHash: string | null;

  @Prop({ type: Date, default: null })
  mutedUntil: Date | null;
}

export const UserSchema = SchemaFactory.createForClass(User);
