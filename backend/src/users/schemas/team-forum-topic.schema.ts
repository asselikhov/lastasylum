import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PlayerTeam } from './player-team.schema';

export type TeamForumTopicDocument = HydratedDocument<TeamForumTopic>;

@Schema({ timestamps: true })
export class TeamForumTopic {
  @Prop({
    type: Types.ObjectId,
    ref: PlayerTeam.name,
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ required: true, trim: true, maxlength: 200 })
  title: string;

  @Prop({ required: true })
  createdByUserId: string;

  @Prop({ type: Date, default: null, index: true })
  lastMessageAt: Date | null;

  @Prop({ required: true, default: 0 })
  messageCount: number;

  @Prop({ type: Types.ObjectId, default: null, index: true })
  pinnedMessageId: Types.ObjectId | null;

  @Prop({ type: Date, default: null })
  pinnedAt: Date | null;

  @Prop({ type: String, default: null })
  pinnedByUserId: string | null;

  createdAt?: Date;
  updatedAt?: Date;
}

export const TeamForumTopicSchema =
  SchemaFactory.createForClass(TeamForumTopic);
TeamForumTopicSchema.index({ teamId: 1, lastMessageAt: -1 });
