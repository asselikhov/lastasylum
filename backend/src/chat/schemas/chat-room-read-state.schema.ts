import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { ChatRoom } from './chat-room.schema';

export type ChatRoomReadStateDocument = HydratedDocument<ChatRoomReadState>;

@Schema({ timestamps: true })
export class ChatRoomReadState {
  @Prop({ type: Types.ObjectId, ref: ChatRoom.name, required: true, index: true })
  roomId: Types.ObjectId;

  @Prop({ type: String, required: true, index: true })
  userId: string;

  /** Highest read message id (ObjectId string) for the room. */
  @Prop({ type: String, required: true })
  lastReadMessageId: string;
}

export const ChatRoomReadStateSchema =
  SchemaFactory.createForClass(ChatRoomReadState);
ChatRoomReadStateSchema.index({ roomId: 1, userId: 1 }, { unique: true });
