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
import { resolveTeamNewsImageMime } from './team-news-image-mime.util';
import { User, UserDocument } from './schemas/user.schema';
import { buildAvatarRelativeUrl } from './user-avatar.util';

const AVATAR_MAX_BYTES = 2 * 1024 * 1024;

export type UploadedUserAvatar = {
  avatarRelativeUrl: string;
  avatarUpdatedAt: string;
};

@Injectable()
export class UserAvatarService {
  private readonly logger = new Logger(UserAvatarService.name);

  constructor(
    @InjectModel(User.name)
    private readonly userModel: Model<UserDocument>,
    private readonly r2: R2Service,
  ) {}

  async upload(input: {
    userId: string;
    buffer: Buffer;
    mimeType: string;
    size: number;
  }): Promise<UploadedUserAvatar> {
    const { buffer, size } = input;
    if (size <= 0) {
      throw new BadRequestException('file is empty');
    }
    if (size > AVATAR_MAX_BYTES) {
      throw new BadRequestException('Avatar exceeds 2 MB limit');
    }

    const mimeType = resolveTeamNewsImageMime(buffer, input.mimeType);
    const ext = mimeType.split('/')[1]?.trim() || 'bin';
    const safeExt = ext.replace(/[^a-zA-Z0-9]+/g, '').slice(0, 8) || 'bin';
    const fileId = new Types.ObjectId();
    const userId = input.userId.trim();
    const key = `profiles/${userId}/${fileId.toString()}.${safeExt}`;

    const user = await this.userModel.findById(userId).exec();
    if (!user) {
      throw new NotFoundException('User not found');
    }

    const previousKey = user.avatarKey?.trim() || null;

    try {
      await this.r2.putObject({
        key,
        body: buffer,
        contentType: mimeType,
        cacheControl: 'private, max-age=31536000, immutable',
      });
    } catch (err) {
      this.logger.error(`R2 putObject failed key=${key}`, err);
      throw new BadGatewayException('USER_AVATAR_R2_PUT_FAILED');
    }

    const updatedAt = new Date();
    user.avatarKey = key;
    user.avatarUpdatedAt = updatedAt;
    await user.save();

    if (previousKey && previousKey !== key) {
      await this.r2.deleteObject(previousKey).catch((err) => {
        this.logger.warn(`Failed to delete previous avatar key=${previousKey}`, err);
      });
    }

    return {
      avatarRelativeUrl: buildAvatarRelativeUrl(userId, key, updatedAt)!,
      avatarUpdatedAt: updatedAt.toISOString(),
    };
  }

  async delete(userId: string): Promise<void> {
    const user = await this.userModel.findById(userId.trim()).exec();
    if (!user) {
      throw new NotFoundException('User not found');
    }
    const previousKey = user.avatarKey?.trim() || null;
    user.avatarKey = null;
    user.avatarUpdatedAt = null;
    await user.save();
    if (previousKey) {
      await this.r2.deleteObject(previousKey).catch((err) => {
        this.logger.warn(`Failed to delete avatar key=${previousKey}`, err);
      });
    }
  }

  async assertMayViewAvatar(viewerUserId: string, targetUserId: string): Promise<UserDocument> {
    const target = await this.userModel.findById(targetUserId.trim()).exec();
    if (!target) {
      throw new NotFoundException('User not found');
    }
    if (viewerUserId.trim() === targetUserId.trim()) {
      return target;
    }
    const viewer = await this.userModel.findById(viewerUserId.trim()).exec();
    if (!viewer) {
      throw new NotFoundException('User not found');
    }
    const viewerTeam = viewer.playerTeamId?.toString() ?? null;
    const targetTeam = target.playerTeamId?.toString() ?? null;
    if (
      viewerTeam &&
      targetTeam &&
      viewerTeam === targetTeam
    ) {
      return target;
    }
    throw new ForbiddenException('Cannot view this avatar');
  }

  async openDownload(userId: string): Promise<{
    stream: NodeJS.ReadableStream;
    mimeType: string;
  }> {
    const user = await this.userModel.findById(userId.trim()).exec();
    if (!user?.avatarKey?.trim()) {
      throw new NotFoundException('Avatar not found');
    }
    const obj = await this.r2.getObjectStream(user.avatarKey);
    return {
      stream: obj.body,
      mimeType: obj.contentType?.trim() || 'image/jpeg',
    };
  }
}
