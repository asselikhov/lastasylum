import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';
import { PinHistoryEntrySchema } from '../../common/pin-history.schema';
import type { PinHistoryEntry } from '../../common/pin-history.util';

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

  @Prop({ type: Types.ObjectId, default: null, index: true })
  pinnedMessageId: Types.ObjectId | null;

  @Prop({ type: Date, default: null })
  pinnedAt: Date | null;

  @Prop({ type: String, default: null })
  pinnedByUserId: string | null;

  @Prop({ type: [PinHistoryEntrySchema], default: [] })
  pinHistory: PinHistoryEntry[];
}

export const ChatRoomSchema = SchemaFactory.createForClass(ChatRoom);
ChatRoomSchema.index({ allianceId: 1, sortOrder: 1 });
