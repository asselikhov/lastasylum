import {
  BadRequestException,
  ForbiddenException,
  Injectable,
} from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model, Types } from 'mongoose';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UsersService } from '../users/users.service';
import { ChatRoomsService } from './chat-rooms.service';
import { Message } from './schemas/message.schema';

type MessageAuthor = {
  userId: string;
  username: string;
  role: AllianceRole;
};

@Injectable()
export class ChatService {
  constructor(
    @InjectModel(Message.name) private readonly messageModel: Model<Message>,
    private readonly usersService: UsersService,
    private readonly chatRoomsService: ChatRoomsService,
  ) {}

  async assertUserMayUseChat(userId: string): Promise<void> {
    const u = await this.usersService.findById(userId);
    if (
      !u ||
      this.usersService.effectiveMembership(u) !== TeamMembershipStatus.ACTIVE
    ) {
      throw new ForbiddenException('Chat is not available for this account');
    }
  }

  private async assertRoomForUser(
    userId: string,
    roomId: string,
  ): Promise<{ allianceId: string; roomObjectId: Types.ObjectId }> {
    const user = await this.usersService.findById(userId);
    if (!user) {
      throw new ForbiddenException('User not found');
    }
    const room = await this.chatRoomsService.findById(roomId);
    if (!room || room.archivedAt) {
      throw new ForbiddenException('Room not found');
    }
    if (room.allianceId !== user.allianceName) {
      throw new ForbiddenException('Room is not available for your alliance');
    }
    return {
      allianceId: user.allianceName,
      roomObjectId: room._id as Types.ObjectId,
    };
  }

  async createMessage(input: {
    text: string;
    author: MessageAuthor;
    roomId: string;
  }) {
    const authorUser = await this.usersService.findById(input.author.userId);
    if (
      !authorUser ||
      this.usersService.effectiveMembership(authorUser) !==
        TeamMembershipStatus.ACTIVE
    ) {
      throw new ForbiddenException('Chat is not available for this account');
    }
    const now = new Date();
    if (authorUser.mutedUntil && authorUser.mutedUntil > now) {
      throw new ForbiddenException(
        'You are temporarily muted in alliance chat',
      );
    }

    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      input.author.userId,
      input.roomId,
    );

    return this.messageModel.create({
      allianceId,
      roomId: roomObjectId,
      text: input.text.trim(),
      senderId: input.author.userId,
      senderUsername: input.author.username,
      senderRole: input.author.role,
    });
  }

  async getRecentMessages(
    userId: string,
    roomId: string,
    options?: { limit?: number; before?: string },
  ) {
    const rawLimit = options?.limit ?? 30;
    const limit = Math.min(100, Math.max(1, Math.floor(rawLimit)));
    const { allianceId, roomObjectId } = await this.assertRoomForUser(
      userId,
      roomId,
    );
    const filter: Record<string, unknown> = {
      allianceId,
      roomId: roomObjectId,
    };
    const before = options?.before?.trim();
    if (before) {
      if (!Types.ObjectId.isValid(before)) {
        throw new BadRequestException('Invalid before cursor');
      }
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    return this.messageModel
      .find(filter)
      .sort({ createdAt: -1 })
      .limit(limit)
      .lean()
      .exec();
  }
}
