import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { AllianceRole } from '../../common/enums/alliance-role.enum';
import { ChatRoom } from './chat-room.schema';

export type MessageDocument = HydratedDocument<Message>;

@Schema({ timestamps: true })
export class Message {
  @Prop({ required: true, index: true })
  allianceId: string;

  @Prop({ type: Types.ObjectId, ref: ChatRoom.name, required: true, index: true })
  roomId: Types.ObjectId;

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
MessageSchema.index({ allianceId: 1, roomId: 1, createdAt: -1 });
