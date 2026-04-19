import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';
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

  /** Optional in-app player squad (separate from allianceName / chat routing). */
  @Prop({ type: MongooseSchema.Types.ObjectId, ref: 'PlayerTeam', default: null })
  playerTeamId: Types.ObjectId | null;

  /** Optional display name for the squad / team (does not change alliance routing). */
  @Prop({ type: String, default: null, trim: true })
  teamDisplayName: string | null;

  /**
   * Three-letter team tag (letters only), shown in chat before the nickname.
   * Must be set together with teamDisplayName (except both cleared).
   */
  @Prop({ type: String, default: null, trim: true })
  teamTag: string | null;

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

  /** Telegram @username without leading @, lowercase (optional). */
  @Prop({ type: String, default: null, trim: true, lowercase: true })
  telegramUsername: string | null;
}

export const UserSchema = SchemaFactory.createForClass(User);
