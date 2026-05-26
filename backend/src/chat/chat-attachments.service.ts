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
import type { MessageAttachment } from './schemas/message.schema';
import { R2Service } from './r2.service';

export type UploadedChatAttachment = {
  fileId: string;
  url: string;
  kind: 'image' | 'file';
  mimeType: string;
  size: number;
  filename: string | null;
};

const IMAGE_MAX_BYTES = 8 * 1024 * 1024;
const FILE_MAX_BYTES = 120 * 1024 * 1024;

@Injectable()
export class ChatAttachmentsService {
  private readonly logger = new Logger(ChatAttachmentsService.name);

  constructor(
    @InjectModel(ChatAttachment.name)
    private readonly attachmentModel: Model<ChatAttachment>,
    private readonly r2: R2Service,
  ) {}

  static isApkUpload(mimeType: string, filename?: string): boolean {
    const mime = mimeType.trim().toLowerCase();
    if (mime === 'application/vnd.android.package-archive') return true;
    const name = (filename ?? '').trim().toLowerCase();
    return name.endsWith('.apk');
  }

  async uploadImage(input: {
    buffer: Buffer;
    filename?: string;
    mimeType: string;
    size: number;
    allianceId: string;
    roomId: Types.ObjectId;
    uploaderUserId: string;
  }): Promise<UploadedChatAttachment> {
    const { buffer, mimeType, size } = input;
    if (!mimeType.startsWith('image/')) {
      throw new BadRequestException('Only image uploads are supported');
    }
    if (size > IMAGE_MAX_BYTES) {
      throw new BadRequestException('Image exceeds 8 MB limit');
    }

    return this.putAttachment({
      buffer,
      mimeType,
      size,
      allianceId: input.allianceId,
      roomId: input.roomId,
      uploaderUserId: input.uploaderUserId,
      kind: 'image',
      filename: null,
      ext: mimeType.split('/')[1]?.trim() || 'bin',
    });
  }

  async uploadFile(input: {
    buffer: Buffer;
    filename: string;
    mimeType: string;
    size: number;
    allianceId: string;
    roomId: Types.ObjectId;
    uploaderUserId: string;
  }): Promise<UploadedChatAttachment> {
    const { buffer, mimeType, size, filename } = input;
    if (!ChatAttachmentsService.isApkUpload(mimeType, filename)) {
      throw new BadRequestException(
        'Only APK files are supported for file uploads',
      );
    }
    if (size > FILE_MAX_BYTES) {
      throw new BadRequestException('File exceeds 120 MB limit');
    }
    if (size <= 0) {
      throw new BadRequestException('file is empty');
    }

    const safeName = filename.replace(/[^\w.\-()+ ]+/g, '_').slice(0, 120);
    const ext = safeName.toLowerCase().endsWith('.apk') ? 'apk' : 'apk';

    return this.putAttachment({
      buffer,
      mimeType: mimeType.trim() || 'application/vnd.android.package-archive',
      size,
      allianceId: input.allianceId,
      roomId: input.roomId,
      uploaderUserId: input.uploaderUserId,
      kind: 'file',
      filename: safeName || 'update.apk',
      ext,
    });
  }

  private async putAttachment(input: {
    buffer: Buffer;
    mimeType: string;
    size: number;
    allianceId: string;
    roomId: Types.ObjectId;
    uploaderUserId: string;
    kind: 'image' | 'file';
    filename: string | null;
    ext: string;
  }): Promise<UploadedChatAttachment> {
    const safeExt =
      input.ext.replace(/[^a-zA-Z0-9]+/g, '').slice(0, 8) || 'bin';
    const fileId = new Types.ObjectId();
    const key = `chat/${input.allianceId}/${input.roomId.toString()}/${fileId.toString()}.${safeExt}`;

    await this.attachmentModel.create({
      _id: fileId,
      allianceId: input.allianceId,
      roomId: input.roomId,
      uploaderUserId: input.uploaderUserId,
      kind: input.kind,
      filename: input.filename,
      key,
      mimeType: input.mimeType,
      size: input.size,
    });

    try {
      await this.r2.putObject({
        key,
        body: input.buffer,
        contentType: input.mimeType,
        cacheControl: 'private, max-age=31536000, immutable',
      });
    } catch (err) {
      this.logger.error(`R2 putObject failed key=${key}`, err);
      await this.attachmentModel
        .deleteOne({ _id: fileId })
        .exec()
        .catch(() => undefined);
      throw new BadGatewayException('CHAT_ATTACHMENT_R2_PUT_FAILED');
    }

    return {
      fileId: fileId.toString(),
      url: `/chat/attachments/${fileId.toString()}`,
      kind: input.kind,
      mimeType: input.mimeType,
      size: input.size,
      filename: input.filename,
    };
  }

  async resolveForRoom(input: {
    allianceId: string;
    roomObjectId: Types.ObjectId;
    attachmentIds: string[];
  }): Promise<MessageAttachment[]> {
    const out: MessageAttachment[] = [];
    for (const rawId of input.attachmentIds) {
      const doc = await this.findAttachment(rawId);
      if (!doc) {
        throw new BadRequestException(`Unknown attachment: ${rawId}`);
      }
      if (doc.allianceId !== input.allianceId) {
        throw new BadRequestException('Attachment alliance mismatch');
      }
      if (doc.roomId.toString() !== input.roomObjectId.toString()) {
        throw new BadRequestException('Attachment belongs to another room');
      }
      out.push({
        kind: doc.kind,
        fileId: new Types.ObjectId(rawId),
        mimeType: doc.mimeType,
        size: doc.size,
        filename: doc.filename ?? null,
      });
    }
    return out;
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
    kind: 'image' | 'file';
    filename: string | null;
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
      kind: doc.kind,
      filename: doc.filename ?? null,
    };
  }
}
