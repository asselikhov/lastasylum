import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';

export type PlayerTeamDocument = HydratedDocument<PlayerTeam>;

@Schema({ timestamps: true, collection: 'playerteams' })
export class PlayerTeam {
  @Prop({ type: MongooseSchema.Types.ObjectId, ref: 'User', required: true })
  leaderUserId: Types.ObjectId;

  @Prop({ required: true, unique: true, uppercase: true, trim: true })
  tag: string;

  @Prop({ required: true, trim: true, maxlength: 48 })
  displayName: string;

  @Prop({
    type: [{ type: MongooseSchema.Types.ObjectId, ref: 'User' }],
    default: [],
  })
  memberUserIds: Types.ObjectId[];
}

export const PlayerTeamSchema = SchemaFactory.createForClass(PlayerTeam);
