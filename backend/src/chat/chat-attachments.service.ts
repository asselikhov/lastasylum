import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectConnection } from '@nestjs/mongoose';
import { GridFSBucket, ObjectId } from 'mongodb';
import { Connection, Types } from 'mongoose';

export type UploadedChatAttachment = {
  fileId: string;
  url: string;
  mimeType: string;
  size: number;
};

@Injectable()
export class ChatAttachmentsService {
  private readonly bucket: GridFSBucket;

  constructor(@InjectConnection() private readonly connection: Connection) {
    const db = this.connection.db;
    if (!db) {
      throw new Error('MongoDB connection is not ready');
    }
    this.bucket = new GridFSBucket(db, {
      bucketName: 'chat_attachments',
    });
  }

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
    const safeName = filename?.trim() || 'image';
    const uploadStream = this.bucket.openUploadStream(safeName, {
      metadata: {
        kind: 'image',
        allianceId: input.allianceId,
        roomId: input.roomId,
        uploaderUserId: input.uploaderUserId,
        mimeType,
        size,
      },
    });
    uploadStream.end(buffer);

    const fileId = await new Promise<ObjectId>((resolve, reject) => {
      uploadStream.on('finish', (file) => resolve(file._id as ObjectId));
      uploadStream.on('error', reject);
    });

    return {
      fileId: fileId.toString(),
      url: `/chat/attachments/${fileId.toString()}`,
      mimeType,
      size,
    };
  }

  openDownloadStream(fileId: string) {
    if (!ObjectId.isValid(fileId)) {
      throw new BadRequestException('Invalid attachment id');
    }
    return this.bucket.openDownloadStream(new ObjectId(fileId));
  }

  async findFileMeta(fileId: string): Promise<{
    _id: ObjectId;
    contentType?: string;
    length?: number;
    metadata?: Record<string, unknown>;
  } | null> {
    if (!ObjectId.isValid(fileId)) return null;
    const files = await this.bucket
      .find({ _id: new ObjectId(fileId) })
      .limit(1)
      .toArray();
    return files[0] ?? null;
  }
}

