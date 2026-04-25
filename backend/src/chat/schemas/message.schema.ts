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

export type MessageReaction = {
  emoji: string;
  userIds: string[];
};

export type MessageForwardedFrom = {
  messageId: Types.ObjectId;
  senderId: string;
  senderUsername: string;
  senderRole: AllianceRole;
  senderTeamTag: string | null;
};

@Schema({ timestamps: true })
export class Message {
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
  senderId: string;

  @Prop({ required: true })
  senderUsername: string;

  @Prop({ type: String, required: true, enum: AllianceRole })
  senderRole: AllianceRole;

  /** Snapshot of sender team tag at send time (optional for legacy messages). */
  @Prop({ type: String, default: null, trim: true })
  senderTeamTag: string | null;

  /** May be empty when the message is image-only (`attachments` non-empty). */
  @Prop({ type: String, required: false, trim: true, default: '' })
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

  /** Edited text marker (Telegram-style "edited"). */
  @Prop({ type: Date, default: null, index: true })
  editedAt: Date | null;

  /** Forwarded message metadata (minimal; points to original message). */
  @Prop({
    type: {
      messageId: { type: Types.ObjectId, required: true },
      senderId: { type: String, required: true },
      senderUsername: { type: String, required: true },
      senderRole: { type: String, required: true },
      senderTeamTag: { type: String, default: null },
    },
    default: null,
  })
  forwardedFrom: MessageForwardedFrom | null;

  /** Emoji reactions with explicit user lists for toggling. */
  @Prop({
    type: [
      {
        emoji: { type: String, required: true, trim: true },
        userIds: { type: [String], default: [] },
      },
    ],
    default: [],
  })
  reactions: MessageReaction[];

  @Prop({ type: Date, default: null, index: true })
  deletedAt: Date | null;

  @Prop({ type: String, default: null })
  deletedByUserId: string | null;
}

export const MessageSchema = SchemaFactory.createForClass(Message);
MessageSchema.index({ allianceId: 1, createdAt: -1 });
MessageSchema.index({ allianceId: 1, roomId: 1, createdAt: -1 });
