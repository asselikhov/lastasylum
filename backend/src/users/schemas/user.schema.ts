import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';
import { DEFAULT_ALLIANCE_ID } from '../../common/constants/default-alliance-id';
import { AllianceRole } from '../../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../../common/enums/team-membership-status.enum';

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

  @Prop({ required: true, default: DEFAULT_ALLIANCE_ID })
  allianceName: string;

  @Prop({
    type: String,
    required: true,
    enum: TeamMembershipStatus,
    default: TeamMembershipStatus.ACTIVE,
  })
  membershipStatus: TeamMembershipStatus;

  @Prop({ type: String, default: null })
  refreshTokenHash: string | null;

  @Prop({ type: Date, default: null })
  mutedUntil: Date | null;

  @Prop({ type: String, default: null })
  passwordResetTokenHash: string | null;

  @Prop({ type: Date, default: null })
  passwordResetExpires: Date | null;

  /** FCM device tokens (max length enforced in service). */
  @Prop({ type: [String], default: [] })
  pushFcmTokens: string[];

  @Prop({ type: Date, default: null })
  lastPresenceAt: Date | null;

  /** ingame | online | away */
  @Prop({ type: String, default: null })
  presenceStatus: string | null;
}

export const UserSchema = SchemaFactory.createForClass(User);
