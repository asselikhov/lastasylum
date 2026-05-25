import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PlayerTeam } from './player-team.schema';

export type TeamNewsReadStateDocument = HydratedDocument<TeamNewsReadState>;

/** Per-user «last seen team news» cursor (survives reinstall; Telegram-style inbox). */
@Schema({ timestamps: true })
export class TeamNewsReadState {
  @Prop({
    type: Types.ObjectId,
    ref: PlayerTeam.name,
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ type: String, required: true, index: true })
  userId: string;

  /** ISO-8601 instant — news with createdAt <= this are read. */
  @Prop({ type: Date, required: true })
  lastSeenCreatedAt: Date;
}

export const TeamNewsReadStateSchema =
  SchemaFactory.createForClass(TeamNewsReadState);
TeamNewsReadStateSchema.index({ teamId: 1, userId: 1 }, { unique: true });
