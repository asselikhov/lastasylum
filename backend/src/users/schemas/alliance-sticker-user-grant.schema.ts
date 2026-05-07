import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Schema as MongooseSchema, Types } from 'mongoose';

export type AllianceStickerUserGrantDocument =
  HydratedDocument<AllianceStickerUserGrant>;

@Schema({ timestamps: true })
export class AllianceStickerUserGrant {
  @Prop({ required: true, trim: true })
  allianceName: string;

  @Prop({ required: true, trim: true })
  packKey: string;

  @Prop({
    type: MongooseSchema.Types.ObjectId,
    ref: 'User',
    required: true,
  })
  userId: Types.ObjectId;
}

export const AllianceStickerUserGrantSchema = SchemaFactory.createForClass(
  AllianceStickerUserGrant,
);

AllianceStickerUserGrantSchema.index(
  { allianceName: 1, packKey: 1, userId: 1 },
  { unique: true },
);
