import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { ChatRoom } from './chat-room.schema';

export type ChatAttachmentDocument = HydratedDocument<ChatAttachment>;

@Schema({ timestamps: true })
export class ChatAttachment {
  @Prop({ required: true, index: true })
  allianceId: string;

  @Prop({
    type: Types.ObjectId,
    ref: ChatRoom.name,
    required: true,
    index: true,
  })
  roomId: Types.ObjectId;

  @Prop({ required: true })
  uploaderUserId: string;

  @Prop({ required: true, enum: ['image', 'file'] })
  kind: 'image' | 'file';

  /** Original filename for `file` kind (e.g. squadrelay-1.2.apk). */
  @Prop({ type: String, default: null, trim: true })
  filename: string | null;

  @Prop({ required: true })
  key: string;

  @Prop({ required: true })
  mimeType: string;

  @Prop({ required: true })
  size: number;
}

export const ChatAttachmentSchema =
  SchemaFactory.createForClass(ChatAttachment);
ChatAttachmentSchema.index({ allianceId: 1, roomId: 1, createdAt: -1 });
