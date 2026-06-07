import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';

export type ChatSystemMetaDocument = HydratedDocument<ChatSystemMeta>;

/** Singleton-style server metadata for chat (admin history wipe watermark). */
@Schema({ timestamps: true, collection: 'chat_system_meta' })
export class ChatSystemMeta {
  @Prop({ type: String, required: true, unique: true, default: 'global' })
  key: string;

  @Prop({ type: Date, default: null })
  historyClearedAt?: Date | null;
}

export const ChatSystemMetaSchema =
  SchemaFactory.createForClass(ChatSystemMeta);
