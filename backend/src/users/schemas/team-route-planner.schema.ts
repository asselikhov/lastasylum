import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';

export type TeamRoutePlannerDocument = HydratedDocument<TeamRoutePlanner>;

/** Маршруты планировщика перемещений команды (JSON-массив RoutePlannerRoute). */
@Schema({ timestamps: true, collection: 'team_route_planner' })
export class TeamRoutePlanner {
  @Prop({
    type: MongooseSchema.Types.ObjectId,
    ref: 'PlayerTeam',
    required: true,
    unique: true,
    index: true,
  })
  teamId: Types.ObjectId;

  /** JSON array of routes (same shape as mobile RoutePlannerRoute). */
  @Prop({ type: String, required: true, default: '[]' })
  routesJson: string;

  @Prop({ required: true, default: 0 })
  updatedAtMs: number;

  @Prop({ required: true, trim: true })
  updatedByUserId: string;
}

export const TeamRoutePlannerSchema =
  SchemaFactory.createForClass(TeamRoutePlanner);
