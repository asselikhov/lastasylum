import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';
import { AllianceRole } from '../../common/enums/alliance-role.enum';

export type MessageDocument = HydratedDocument<Message>;

@Schema({ timestamps: true })
export class Message {
  @Prop({ required: true, index: true })
  allianceId: string;

  @Prop({ required: true })
  senderId: string;

  @Prop({ required: true })
  senderUsername: string;

  @Prop({ type: String, required: true, enum: AllianceRole })
  senderRole: AllianceRole;

  @Prop({ required: true, trim: true })
  text: string;
}

export const MessageSchema = SchemaFactory.createForClass(Message);
MessageSchema.index({ allianceId: 1, createdAt: -1 });
