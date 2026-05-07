import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PlayerTeam } from './player-team.schema';

import { TeamForumTopic } from './team-forum-topic.schema';

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

  @Prop({ type: String, required: true, trim: true, default: '' })
  text: string;

  /** Optional image; same storage as team news attachments (GET …/news/attachments/:id). */
  @Prop({ type: Types.ObjectId, default: null })
  imageFileId: Types.ObjectId | null;

  @Prop({ type: String, default: null })
  imageMimeType: string | null;

  @Prop({ type: Number, default: null })
  imageSize: number | null;

  @Prop({ type: Date, default: null })
  editedAt: Date | null;

  @Prop({ type: Date, default: null, index: true })
  deletedAt: Date | null;

  @Prop({ type: String, default: null })
  deletedByUserId: string | null;

  createdAt?: Date;
  updatedAt?: Date;
}

export const TeamForumMessageSchema =
  SchemaFactory.createForClass(TeamForumMessage);
TeamForumMessageSchema.index({ teamId: 1, topicId: 1, createdAt: -1 });
