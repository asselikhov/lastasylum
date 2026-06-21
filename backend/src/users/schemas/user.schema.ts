import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';
import { DEFAULT_ALLIANCE_ID } from '../../common/constants/default-alliance-id';
import { AllianceRole } from '../../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../../common/enums/team-membership-status.enum';
import { GameIdentity, GameIdentitySchema } from './game-identity.schema';

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
    default: AllianceRole.MEMBER,
  })
  role: AllianceRole;

  @Prop({ required: true, default: DEFAULT_ALLIANCE_ID })
  allianceName: string;

  /** Optional in-app player squad (separate from allianceName / chat routing). */
  @Prop({
    type: MongooseSchema.Types.ObjectId,
    ref: 'PlayerTeam',
    default: null,
  })
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

  /**
   * Последний пинг «в игре» с активной оверлей-панелью (status ingame).
   * Не обновляется при online/away из основного приложения.
   */
  @Prop({ type: Date, default: null })
  lastPresenceAt: Date | null;

  /** Последняя активность в приложении SquadRelay (вход, online, away). */
  @Prop({ type: Date, default: null })
  lastAppActiveAt: Date | null;

  /** Последняя известная версия Android-клиента (из User-Agent). */
  @Prop({ type: String, default: null, trim: true })
  lastAppVersionName: string | null;

  @Prop({ type: Number, default: null })
  lastAppVersionCode: number | null;

  @Prop({ type: Date, default: null })
  lastAppVersionReportedAt: Date | null;

  /** Throttle [UsersService.toSafeUser] squad reconcile (heavy DB). */
  @Prop({ type: Date, default: null })
  lastProfileReconcileAt: Date | null;

  /** ingame | online | away */
  @Prop({ type: String, default: null })
  presenceStatus: string | null;

  /** Telegram @username without leading @, lowercase (optional). */
  @Prop({ type: String, default: null, trim: true, lowercase: true })
  telegramUsername: string | null;

  /** R2 object key for profile avatar (`profiles/{userId}/…`). */
  @Prop({ type: String, default: null, trim: true })
  avatarKey: string | null;

  /** Bumped on avatar upload/delete (cache-bust for clients). */
  @Prop({ type: Date, default: null })
  avatarUpdatedAt: Date | null;

  /** @deprecated Synced with gameEventPushEnabled.hq_excavation */
  @Prop({ type: Boolean, default: true })
  excavationPushEnabled: boolean;

  /** Per game-event push toggles; missing key = enabled. */
  @Prop({ type: Object, default: {} })
  gameEventPushEnabled: Record<string, boolean>;

  /** Игровые ники по серверам; команда привязана к записи, не к аккаунту целиком. */
  @Prop({ type: [GameIdentitySchema], default: [] })
  gameIdentities: GameIdentity[];

  @Prop({ type: MongooseSchema.Types.ObjectId, default: null })
  activeGameIdentityId: Types.ObjectId | null;

  /** R5 admin: overlay HUD «Поиск игрока/альянса в игре» for this account. */
  @Prop({ type: Boolean, default: false })
  overlayGameSearchEnabled: boolean;
}

export const UserSchema = SchemaFactory.createForClass(User);
UserSchema.index({
  membershipStatus: 1,
  allianceName: 1,
  pushFcmTokens: 1,
});
UserSchema.index({
  membershipStatus: 1,
  playerTeamId: 1,
  pushFcmTokens: 1,
});
UserSchema.index({ playerTeamId: 1 });
UserSchema.index({ 'gameIdentities.serverNumber': 1 });
UserSchema.index({ 'gameIdentities.playerTeamId': 1 });
UserSchema.index({
  playerTeamId: 1,
  membershipStatus: 1,
  presenceStatus: 1,
  lastPresenceAt: -1,
});
