import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';

export type GameIdentityDocument = HydratedDocument<GameIdentity>;

/** Игровой ник на конкретном сервере; команда привязана к этой записи. */
@Schema({ _id: true })
export class GameIdentity {
  _id?: Types.ObjectId;
  @Prop({ required: true, min: 1, max: 9999 })
  serverNumber: number;

  @Prop({ required: true, trim: true })
  gameNickname: string;

  @Prop({
    type: MongooseSchema.Types.ObjectId,
    ref: 'PlayerTeam',
    default: null,
  })
  playerTeamId: Types.ObjectId | null;
}

export const GameIdentitySchema = SchemaFactory.createForClass(GameIdentity);
