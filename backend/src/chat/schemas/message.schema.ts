import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { AllianceRole } from '../../common/enums/alliance-role.enum';
import { ChatRoom } from './chat-room.schema';

export type MessageDocument = HydratedDocument<Message>;

export type MessageAttachment = {
  kind: 'image';
  fileId: Types.ObjectId;
  mimeType: string;
  size: number;
};

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

  /** Snapshot of sender team tag at send time (optional for legacy messages). */
  @Prop({ type: String, default: null, trim: true })
  senderTeamTag: string | null;

  @Prop({ required: true, trim: true })
  text: string;

  @Prop({
    type: [
      {
        kind: { type: String, required: true },
        fileId: { type: Types.ObjectId, required: true },
        mimeType: { type: String, required: true },
        size: { type: Number, required: true },
      },
    ],
    default: [],
  })
  attachments: MessageAttachment[];

  @Prop({ type: Types.ObjectId, ref: Message.name, default: null, index: true })
  replyToMessageId: Types.ObjectId | null;

  @Prop({ type: Date, default: null, index: true })
  deletedAt: Date | null;

  @Prop({ type: String, default: null })
  deletedByUserId: string | null;
}

export const MessageSchema = SchemaFactory.createForClass(Message);
MessageSchema.index({ allianceId: 1, createdAt: -1 });
MessageSchema.index({ allianceId: 1, roomId: 1, createdAt: -1 });
