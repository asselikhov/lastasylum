import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PlayerTeam } from '../../users/schemas/player-team.schema';

export type OverlayReactionLogReadStateDocument =
  HydratedDocument<OverlayReactionLogReadState>;

@Schema({ timestamps: true })
export class OverlayReactionLogReadState {
  @Prop({
    type: Types.ObjectId,
    ref: PlayerTeam.name,
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ type: String, required: true, index: true })
  userId: string;

  @Prop({ type: String, default: null })
  lastSeenLogId: string | null;
}

export const OverlayReactionLogReadStateSchema = SchemaFactory.createForClass(
  OverlayReactionLogReadState,
);
OverlayReactionLogReadStateSchema.index(
  { teamId: 1, userId: 1 },
  { unique: true },
);
