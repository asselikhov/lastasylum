import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';

export type TeamJoinRequestDocument = HydratedDocument<TeamJoinRequest>;

export enum TeamJoinRequestStatus {
  PENDING = 'pending',
  ACCEPTED = 'accepted',
  REJECTED = 'rejected',
}

@Schema({ timestamps: true, collection: 'teamjoinrequests' })
export class TeamJoinRequest {
  @Prop({
    type: MongooseSchema.Types.ObjectId,
    ref: 'PlayerTeam',
    required: true,
  })
  teamId: Types.ObjectId;

  @Prop({ type: MongooseSchema.Types.ObjectId, ref: 'User', required: true })
  requesterUserId: Types.ObjectId;

  @Prop({
    type: String,
    required: true,
    enum: TeamJoinRequestStatus,
    default: TeamJoinRequestStatus.PENDING,
  })
  status: TeamJoinRequestStatus;
}

export const TeamJoinRequestSchema =
  SchemaFactory.createForClass(TeamJoinRequest);

TeamJoinRequestSchema.index(
  { teamId: 1, requesterUserId: 1, status: 1 },
  { unique: true, partialFilterExpression: { status: 'pending' } },
);
