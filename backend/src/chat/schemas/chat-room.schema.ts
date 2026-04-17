import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';

export type ChatRoomDocument = HydratedDocument<ChatRoom>;

@Schema({ timestamps: true })
export class ChatRoom {
  @Prop({ required: true, index: true })
  allianceId: string;

  @Prop({ required: true, trim: true })
  title: string;

  @Prop({ required: true, default: 0 })
  sortOrder: number;

  @Prop({ type: Date, default: null })
  archivedAt: Date | null;
}

export const ChatRoomSchema = SchemaFactory.createForClass(ChatRoom);
ChatRoomSchema.index({ allianceId: 1, sortOrder: 1 });
