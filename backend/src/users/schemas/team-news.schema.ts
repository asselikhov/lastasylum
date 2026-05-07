import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';

export type TeamNewsDocument = HydratedDocument<TeamNews>;

const ImageSlotSchema = new MongooseSchema(
  {
    fileId: {
      type: MongooseSchema.Types.ObjectId,
      required: true,
    },
    mimeType: { type: String, required: true },
    size: { type: Number, required: true },
  },
  { _id: false },
);

const PollOptionSchema = new MongooseSchema(
  {
    id: { type: String, required: true },
    text: { type: String, required: true },
  },
  { _id: false },
);

const PollVoteSchema = new MongooseSchema(
  {
    userId: { type: String, required: true },
    optionId: { type: String, required: true },
  },
  { _id: false },
);

const PollSchema = new MongooseSchema(
  {
    question: { type: String, required: true },
    options: { type: [PollOptionSchema], required: true },
    votes: { type: [PollVoteSchema], default: [] },
  },
  { _id: false },
);

@Schema({ timestamps: true, collection: 'team_news' })
export class TeamNews {
  @Prop({
    type: MongooseSchema.Types.ObjectId,
    ref: 'PlayerTeam',
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ required: true, index: true })
  authorUserId: string;

  @Prop({ required: true, trim: true, maxlength: 200 })
  title: string;

  @Prop({ required: true, trim: true, maxlength: 20000 })
  body: string;

  @Prop({ type: [ImageSlotSchema], default: [] })
  imageAttachments: Array<{
    fileId: Types.ObjectId;
    mimeType: string;
    size: number;
  }>;

  @Prop({ type: PollSchema, default: null })
  poll: {
    question: string;
    options: Array<{ id: string; text: string }>;
    votes: Array<{ userId: string; optionId: string }>;
  } | null;
}

export const TeamNewsSchema = SchemaFactory.createForClass(TeamNews);
TeamNewsSchema.index({ teamId: 1, _id: -1 });
