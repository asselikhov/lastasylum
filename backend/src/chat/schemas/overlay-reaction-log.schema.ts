import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PlayerTeam } from '../../users/schemas/player-team.schema';

export type OverlayReactionLogDocument = HydratedDocument<OverlayReactionLog>;

@Schema({ timestamps: true })
export class OverlayReactionLog {
  @Prop({
    type: Types.ObjectId,
    ref: PlayerTeam.name,
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ type: String, required: true, index: true })
  senderUserId: string;

  @Prop({ type: String, required: true, trim: true })
  senderUsername: string;

  @Prop({ type: String, default: null, index: true })
  targetUserId: string | null;

  @Prop({ type: String, default: null, trim: true })
  targetUsername: string | null;

  @Prop({ type: String, required: true, trim: true })
  reaction: string;

  @Prop({
    type: String,
    required: true,
    enum: ['personal', 'broadcast'],
    index: true,
  })
  visibility: 'personal' | 'broadcast';

  @Prop({ type: Types.ObjectId, default: null, index: true })
  replyToLogId: Types.ObjectId | null;

  /** Denormalized parent snapshot for reply entries (UI quote row). */
  @Prop({
    type: {
      _id: { type: Types.ObjectId, required: true },
      reaction: { type: String, required: true, trim: true },
      visibility: { type: String, required: true, enum: ['personal', 'broadcast'] },
      senderUserId: { type: String, required: true },
      senderUsername: { type: String, required: true, trim: true },
      targetUserId: { type: String, default: null },
      targetUsername: { type: String, default: null, trim: true },
    },
    default: null,
    _id: false,
  })
  replyToLog: {
    _id: Types.ObjectId;
    reaction: string;
    visibility: 'personal' | 'broadcast';
    senderUserId: string;
    senderUsername: string;
    targetUserId: string | null;
    targetUsername: string | null;
  } | null;

  /** Emoji reactions on log entry (chat-style). */
  @Prop({
    type: [
      {
        emoji: { type: String, required: true, trim: true },
        userIds: { type: [String], default: [] },
      },
    ],
    default: [],
  })
  reactions: { emoji: string; userIds: string[] }[];
}

export const OverlayReactionLogSchema =
  SchemaFactory.createForClass(OverlayReactionLog);
OverlayReactionLogSchema.index({ teamId: 1, _id: -1 });
OverlayReactionLogSchema.index({ teamId: 1, visibility: 1, _id: -1 });
OverlayReactionLogSchema.index({ teamId: 1, targetUserId: 1, _id: -1 });
OverlayReactionLogSchema.index({ teamId: 1, senderUserId: 1, _id: -1 });
OverlayReactionLogSchema.index({ teamId: 1, replyToLogId: 1, _id: -1 });
