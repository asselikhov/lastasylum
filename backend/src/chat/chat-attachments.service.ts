import {
  BadGatewayException,
  BadRequestException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { ChatAttachment } from './schemas/chat-attachment.schema';
import { R2Service } from './r2.service';

export type UploadedChatAttachment = {
  fileId: string;
  url: string;
  mimeType: string;
  size: number;
};

@Injectable()
export class ChatAttachmentsService {
  private readonly logger = new Logger(ChatAttachmentsService.name);

  constructor(
    @InjectModel(ChatAttachment.name)
    private readonly attachmentModel: Model<ChatAttachment>,
    private readonly r2: R2Service,
  ) {}

  async uploadImage(input: {
    buffer: Buffer;
    filename: string;
    mimeType: string;
    size: number;
    allianceId: string;
    roomId: Types.ObjectId;
    uploaderUserId: string;
  }): Promise<UploadedChatAttachment> {
    const { buffer, filename, mimeType, size } = input;
    if (!mimeType.startsWith('image/')) {
      throw new BadRequestException('Only image uploads are supported');
    }
    const created = await this.attachmentModel.create({
      allianceId: input.allianceId,
      roomId: input.roomId,
      uploaderUserId: input.uploaderUserId,
      kind: 'image',
      key: '', // set below after key is computed
      mimeType,
      size,
    });

    const ext = mimeType.split('/')[1]?.trim() || 'bin';
    const safeExt = ext.replace(/[^a-zA-Z0-9]+/g, '').slice(0, 8) || 'bin';
    const key = `chat/${input.allianceId}/${input.roomId.toString()}/${created._id.toString()}.${safeExt}`;
    created.key = key;
    await created.save();

    try {
      await this.r2.putObject({
        key,
        body: buffer,
        contentType: mimeType,
        cacheControl: 'private, max-age=31536000, immutable',
      });
    } catch (err) {
      this.logger.error(`R2 putObject failed key=${key}`, err);
      await this.attachmentModel.deleteOne({ _id: created._id }).exec().catch(() => undefined);
      throw new BadGatewayException('CHAT_ATTACHMENT_R2_PUT_FAILED');
    }

    return {
      fileId: created._id.toString(),
      url: `/chat/attachments/${created._id.toString()}`,
      mimeType,
      size,
    };
  }

  async findAttachment(fileId: string): Promise<ChatAttachment | null> {
    if (!Types.ObjectId.isValid(fileId)) return null;
    return this.attachmentModel
      .findById(new Types.ObjectId(fileId))
      .lean<ChatAttachment | null>()
      .exec();
  }

  async openR2Download(fileId: string): Promise<{
    stream: NodeJS.ReadableStream;
    mimeType: string;
    size: number;
    roomId: Types.ObjectId;
    allianceId: string;
  }> {
    const doc = await this.findAttachment(fileId);
    if (!doc) throw new NotFoundException('Attachment not found');
    const res = await this.r2.getObjectStream(doc.key);
    return {
      stream: res.body,
      mimeType: doc.mimeType,
      size: doc.size,
      roomId: doc.roomId,
      allianceId: doc.allianceId,
    };
  }
}

