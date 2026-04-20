import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';
import { PlayerTeamMemberRole } from '../../common/enums/player-team-member-role.enum';

export type PlayerTeamDocument = HydratedDocument<PlayerTeam>;

const SquadMemberSchema = new MongooseSchema(
  {
    userId: {
      type: MongooseSchema.Types.ObjectId,
      ref: 'User',
      required: true,
    },
    role: {
      type: String,
      enum: Object.values(PlayerTeamMemberRole),
      required: true,
    },
  },
  { _id: false },
);

@Schema({ timestamps: true, collection: 'playerteams' })
export class PlayerTeam {
  @Prop({ type: MongooseSchema.Types.ObjectId, ref: 'User', required: true })
  leaderUserId: Types.ObjectId;

  @Prop({ required: true, unique: true, uppercase: true, trim: true })
  tag: string;

  @Prop({ required: true, trim: true, maxlength: 48 })
  displayName: string;

  @Prop({
    type: [SquadMemberSchema],
    default: [],
  })
  squadMembers: { userId: Types.ObjectId; role: PlayerTeamMemberRole }[];

  /**
   * @deprecated Legacy membership list; migrated to `squadMembers` on read.
   */
  @Prop({
    type: [{ type: MongooseSchema.Types.ObjectId, ref: 'User' }],
    required: false,
  })
  memberUserIds?: Types.ObjectId[];
}

export const PlayerTeamSchema = SchemaFactory.createForClass(PlayerTeam);
