import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PlayerTeam } from './player-team.schema';

import { TeamForumTopic } from './team-forum-topic.schema';
import { PlayerTeamMemberRole } from '../../common/enums/player-team-member-role.enum';

export type TeamForumMessageDocument = HydratedDocument<TeamForumMessage>;

@Schema({ timestamps: true })
export class TeamForumMessage {
  @Prop({
    type: Types.ObjectId,
    ref: TeamForumTopic.name,
    required: true,
    index: true,
  })
  topicId: Types.ObjectId;

  @Prop({
    type: Types.ObjectId,
    ref: PlayerTeam.name,
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ required: true })
  senderUserId: string;

  @Prop({ required: true, trim: true })
  senderUsername: string;

  /**
   * Sender snapshot at send time.
   * Matches in-app squad role strings: R1..R5.
   */
  @Prop({
    required: true,
    type: String,
    trim: true,
    enum: Object.values(PlayerTeamMemberRole),
  })
  senderRole: PlayerTeamMemberRole;

  /** Sender team tag snapshot at send time (3-letter, may be null). */
  @Prop({ type: String, default: null, trim: true })
  senderTeamTag: string | null;

  @Prop({ type: Number, default: null })
  senderServerNumber: number | null;

  /** May be empty for attachment-only posts; API validates text vs attachments. */
  @Prop({ type: String, trim: true, default: '' })
  text: string;

  /** Optional reply target within the same team+topic (deleted targets are not allowed). */
  @Prop({ type: Types.ObjectId, default: null })
  replyToMessageId: Types.ObjectId | null;

  /** Optional image; same storage as team news attachments (GET …/news/attachments/:id). */
  @Prop({ type: Types.ObjectId, default: null })
  imageFileId: Types.ObjectId | null;

  /** Optional album (preferred). Legacy [imageFileId] kept for backward compatibility. */
  @Prop({ type: [Types.ObjectId], default: [] })
  imageFileIds: Types.ObjectId[];

  @Prop({ type: String, default: null })
  imageMimeType: string | null;

  @Prop({ type: Number, default: null })
  imageSize: number | null;

  /** Optional APK/file (R5 forum upload via team news attachments storage). */
  @Prop({ type: Types.ObjectId, default: null })
  fileFileId: Types.ObjectId | null;

  @Prop({ type: String, default: null })
  fileFilename: string | null;

  @Prop({ type: Date, default: null })
  editedAt: Date | null;

  @Prop({ type: Date, default: null, index: true })
  deletedAt: Date | null;

  @Prop({ type: String, default: null })
  deletedByUserId: string | null;

  /** Forwarded message metadata (minimal; points to original forum message). */
  @Prop({
    type: {
      messageId: { type: Types.ObjectId, required: true },
      senderUserId: { type: String, required: true },
      senderUsername: { type: String, required: true },
      senderRole: { type: String, required: true },
      senderTeamTag: { type: String, default: null },
      senderServerNumber: { type: Number, default: null },
    },
    default: null,
  })
  forwardedFrom: {
    messageId: Types.ObjectId;
    senderUserId: string;
    senderUsername: string;
    senderRole: PlayerTeamMemberRole;
    senderTeamTag: string | null;
    senderServerNumber: number | null;
  } | null;

  createdAt?: Date;
  updatedAt?: Date;
}

export const TeamForumMessageSchema =
  SchemaFactory.createForClass(TeamForumMessage);
TeamForumMessageSchema.index({ teamId: 1, topicId: 1, createdAt: -1 });
