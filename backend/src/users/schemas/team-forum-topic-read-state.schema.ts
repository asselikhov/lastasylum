import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { TeamForumTopic } from './team-forum-topic.schema';

export type TeamForumTopicReadStateDocument =
  HydratedDocument<TeamForumTopicReadState>;

@Schema({ timestamps: true })
export class TeamForumTopicReadState {
  @Prop({
    type: Types.ObjectId,
    ref: TeamForumTopic.name,
    required: true,
    index: true,
  })
  topicId: Types.ObjectId;

  @Prop({ type: String, required: true, index: true })
  userId: string;

  @Prop({ type: String, required: true })
  lastReadMessageId: string;
}

export const TeamForumTopicReadStateSchema = SchemaFactory.createForClass(
  TeamForumTopicReadState,
);
TeamForumTopicReadStateSchema.index(
  { topicId: 1, userId: 1 },
  { unique: true },
);
