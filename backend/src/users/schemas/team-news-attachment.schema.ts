import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument, Types } from 'mongoose';

export type TeamNewsAttachmentDocument = HydratedDocument<TeamNewsAttachment>;

@Schema({ timestamps: true, collection: 'team_news_attachments' })
export class TeamNewsAttachment {
  @Prop({
    type: Types.ObjectId,
    ref: 'PlayerTeam',
    required: true,
    index: true,
  })
  teamId: Types.ObjectId;

  @Prop({ required: true })
  uploaderUserId: string;

  @Prop({
    type: String,
    required: true,
    enum: ['image', 'file'],
    default: 'image',
  })
  kind: 'image' | 'file';

  /** Original filename for kind=file (APK uploads). */
  @Prop({ type: String, default: null })
  filename: string | null;

  @Prop({ required: true })
  key: string;

  @Prop({ required: true })
  mimeType: string;

  @Prop({ required: true })
  size: number;
}

export const TeamNewsAttachmentSchema =
  SchemaFactory.createForClass(TeamNewsAttachment);
