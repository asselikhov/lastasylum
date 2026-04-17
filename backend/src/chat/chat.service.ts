import { ForbiddenException, Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UsersService } from '../users/users.service';
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

  async createMessage(input: {
    allianceId: string;
    text: string;
    author: MessageAuthor;
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

    return this.messageModel.create({
      allianceId: input.allianceId,
      text: input.text.trim(),
      senderId: input.author.userId,
      senderUsername: input.author.username,
      senderRole: input.author.role,
    });
  }

  async getRecentMessages(allianceId: string, limit = 30) {
    return this.messageModel
      .find({ allianceId })
      .sort({ createdAt: -1 })
      .limit(limit)
      .lean()
      .exec();
  }
}
