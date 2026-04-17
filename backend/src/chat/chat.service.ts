import { ForbiddenException, Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { AllianceRole } from '../common/enums/alliance-role.enum';
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

  async createMessage(input: {
    allianceId: string;
    text: string;
    author: MessageAuthor;
  }) {
    const authorUser = await this.usersService.findById(input.author.userId);
    const now = new Date();
    if (authorUser?.mutedUntil && authorUser.mutedUntil > now) {
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
