import {
  BadGatewayException,
  BadRequestException,
  ForbiddenException,
  Injectable,
  Logger,
  NotFoundException,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { R2Service } from '../chat/r2.service';
import {
  TeamNewsAttachment,
  TeamNewsAttachmentDocument,
} from './schemas/team-news-attachment.schema';

export type UploadedTeamNewsImage = {
  fileId: string;
  url: string;
  mimeType: string;
  size: number;
};

@Injectable()
export class TeamNewsAttachmentsService {
  private readonly logger = new Logger(TeamNewsAttachmentsService.name);

  constructor(
    @InjectModel(TeamNewsAttachment.name)
    private readonly attachmentModel: Model<TeamNewsAttachmentDocument>,
    private readonly r2: R2Service,
  ) {}

  async uploadImage(input: {
    teamId: Types.ObjectId;
    uploaderUserId: string;
    buffer: Buffer;
    mimeType: string;
    size: number;
  }): Promise<UploadedTeamNewsImage> {
    const { buffer, mimeType, size } = input;
    if (!mimeType.startsWith('image/')) {
      throw new BadRequestException('Only image uploads are supported');
    }

    const ext = mimeType.split('/')[1]?.trim() || 'bin';
    const safeExt = ext.replace(/[^a-zA-Z0-9]+/g, '').slice(0, 8) || 'bin';
    const fileId = new Types.ObjectId();
    const key = `team-news/${input.teamId.toString()}/${fileId.toString()}.${safeExt}`;

    await this.attachmentModel.create({
      _id: fileId,
      teamId: input.teamId,
      uploaderUserId: input.uploaderUserId,
      kind: 'image',
      key,
      mimeType,
      size,
    });

    try {
      await this.r2.putObject({
        key,
        body: buffer,
        contentType: mimeType,
        cacheControl: 'private, max-age=31536000, immutable',
      });
    } catch (err) {
      this.logger.error(`R2 putObject failed key=${key}`, err);
      await this.attachmentModel
        .deleteOne({ _id: fileId })
        .exec()
        .catch(() => undefined);
      throw new BadGatewayException('TEAM_NEWS_ATTACHMENT_R2_PUT_FAILED');
    }

    return {
      fileId: fileId.toString(),
      url: `/teams/${input.teamId.toString()}/news/attachments/${fileId.toString()}`,
      mimeType,
      size,
    };
  }

  async findById(fileId: string): Promise<TeamNewsAttachment | null> {
    if (!Types.ObjectId.isValid(fileId)) return null;
    return this.attachmentModel
      .findById(new Types.ObjectId(fileId))
      .lean<TeamNewsAttachment | null>()
      .exec();
  }

  async assertTeamImageSlots(
    teamId: Types.ObjectId,
    fileIds: string[],
    uploaderUserId: string,
  ): Promise<Array<{ fileId: Types.ObjectId; mimeType: string; size: number }>> {
    if (fileIds.length === 0) return [];
    const oids = fileIds.map((id) => {
      if (!Types.ObjectId.isValid(id)) {
        throw new BadRequestException('Invalid image file id');
      }
      return new Types.ObjectId(id);
    });
    const docs = await this.attachmentModel
      .find({ _id: { $in: oids }, teamId })
      .lean<
        Array<{
          _id: Types.ObjectId;
          uploaderUserId: string;
          mimeType: string;
          size: number;
        }>
      >()
      .exec();
    if (docs.length !== oids.length) {
      throw new BadRequestException('One or more images not found for this team');
    }
    for (const d of docs) {
      if (d.uploaderUserId !== uploaderUserId) {
        throw new ForbiddenException(
          'Images must be uploaded by the author of the news post',
        );
      }
    }
    return docs.map((d) => ({
      fileId: d._id,
      mimeType: d.mimeType,
      size: d.size,
    }));
  }

  /** Forum: any team member may attach their own pre-uploaded images (not news-author–only). */
  async assertForumAttachmentForSender(
    teamId: Types.ObjectId,
    fileIdRaw: string,
    uploaderUserId: string,
  ): Promise<{ fileId: Types.ObjectId; mimeType: string; size: number }> {
    if (!Types.ObjectId.isValid(fileIdRaw)) {
      throw new BadRequestException('Invalid image file id');
    }
    const fileId = new Types.ObjectId(fileIdRaw);
    const doc = await this.attachmentModel
      .findOne({ _id: fileId, teamId })
      .lean<{ _id: Types.ObjectId; uploaderUserId: string; mimeType: string; size: number } | null>()
      .exec();
    if (!doc) {
      throw new BadRequestException('Image not found for this team');
    }
    if (doc.uploaderUserId !== uploaderUserId) {
      throw new ForbiddenException(
        'Image must be uploaded by the message sender',
      );
    }
    return {
      fileId: doc._id,
      mimeType: doc.mimeType,
      size: doc.size,
    };
  }

  async openDownloadForTeam(
    teamId: Types.ObjectId,
    fileId: string,
  ): Promise<{
    stream: NodeJS.ReadableStream;
    mimeType: string;
    size: number;
  }> {
    const doc = await this.findById(fileId);
    if (!doc || !doc.teamId.equals(teamId)) {
      throw new NotFoundException('Attachment not found');
    }
    const res = await this.r2.getObjectStream(doc.key);
    return {
      stream: res.body,
      mimeType: doc.mimeType,
      size: doc.size,
    };
  }

  async deleteByIds(ids: Types.ObjectId[]): Promise<void> {
    if (ids.length === 0) return;
    await this.attachmentModel.deleteMany({ _id: { $in: ids } }).exec();
  }
}
