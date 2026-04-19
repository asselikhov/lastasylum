import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  NotFoundException,
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

type MessageLean = {
  _id: Types.ObjectId | string;
  allianceId: string;
  roomId: Types.ObjectId | string;
  senderId: string;
  senderUsername: string;
  senderRole: AllianceRole;
  text: string;
  replyToMessageId?: Types.ObjectId | string | null;
  deletedAt?: Date | null;
  deletedByUserId?: string | null;
  createdAt?: Date | null;
  updatedAt?: Date | null;
};

export type ChatMessageReplyPreview = {
  _id: string;
  senderId: string;
  senderUsername: string;
  senderRole: AllianceRole;
  text: string;
  createdAt: string | null;
  deletedAt: string | null;
};

export type ChatMessageView = {
  _id: string;
  allianceId: string;
  roomId: string;
  senderId: string;
  senderUsername: string;
  senderRole: AllianceRole;
  text: string;
  createdAt: string | null;
  updatedAt: string | null;
  replyToMessageId: string | null;
  replyTo: ChatMessageReplyPreview | null;
  deletedAt: string | null;
  deletedByUserId: string | null;
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

  private asIdString(id: Types.ObjectId | string | null | undefined): string | null {
    if (!id) return null;
    return typeof id === 'string' ? id : id.toString();
  }

  private toIso(date: Date | null | undefined): string | null {
    return date ? date.toISOString() : null;
  }

  private serializeReplyPreview(message: MessageLean): ChatMessageReplyPreview {
    return {
      _id: this.asIdString(message._id)!,
      senderId: message.senderId,
      senderUsername: message.senderUsername,
      senderRole: message.senderRole,
      text: message.deletedAt ? '' : message.text,
      createdAt: this.toIso(message.createdAt),
      deletedAt: this.toIso(message.deletedAt),
    };
  }

  private serializeMessage(
    message: MessageLean,
    replyMap?: Map<string, MessageLean>,
  ): ChatMessageView {
    const replyToMessageId = this.asIdString(message.replyToMessageId);
    const replyTarget = replyToMessageId ? replyMap?.get(replyToMessageId) : null;
    return {
      _id: this.asIdString(message._id)!,
      allianceId: message.allianceId,
      roomId: this.asIdString(message.roomId)!,
      senderId: message.senderId,
      senderUsername: message.senderUsername,
      senderRole: message.senderRole,
      text: message.deletedAt ? '' : message.text,
      createdAt: this.toIso(message.createdAt),
      updatedAt: this.toIso(message.updatedAt),
      replyToMessageId,
      replyTo: replyTarget ? this.serializeReplyPreview(replyTarget) : null,
      deletedAt: this.toIso(message.deletedAt),
      deletedByUserId: message.deletedByUserId ?? null,
    };
  }

  private async loadReplyMap(messages: MessageLean[]): Promise<Map<string, MessageLean>> {
    const replyIds = [...new Set(
      messages
        .map((message) => this.asIdString(message.replyToMessageId))
        .filter((value): value is string => Boolean(value)),
    )];
    if (replyIds.length == 0) {
      return new Map();
    }
    const replyDocs = await this.messageModel
      .find({ _id: { $in: replyIds.map((id) => new Types.ObjectId(id)) } })
      .lean<MessageLean[]>()
      .exec();
    return new Map(
      replyDocs.map((doc) => [this.asIdString(doc._id)!, doc]),
    );
  }

  private async enrichMessages(messages: MessageLean[]): Promise<ChatMessageView[]> {
    const replyMap = await this.loadReplyMap(messages);
    return messages.map((message) => this.serializeMessage(message, replyMap));
  }

  private async getReplyTarget(
    allianceId: string,
    roomObjectId: Types.ObjectId,
    replyToMessageId?: string,
  ): Promise<MessageLean | null> {
    const replyId = replyToMessageId?.trim();
    if (!replyId) return null;
    if (!Types.ObjectId.isValid(replyId)) {
      throw new BadRequestException('Invalid reply target');
    }
    const replyTarget = await this.messageModel
      .findOne({
        _id: new Types.ObjectId(replyId),
        allianceId,
        roomId: roomObjectId,
      })
      .lean<MessageLean | null>()
      .exec();
    if (!replyTarget || replyTarget.deletedAt) {
      throw new BadRequestException('Reply target is unavailable');
    }
    return replyTarget;
  }

  async createMessage(input: {
    text: string;
    author: MessageAuthor;
    roomId: string;
    replyToMessageId?: string;
  }): Promise<ChatMessageView> {
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
    const replyTarget = await this.getReplyTarget(
      allianceId,
      roomObjectId,
      input.replyToMessageId,
    );

    const created = await this.messageModel.create({
      allianceId,
      roomId: roomObjectId,
      text: input.text.trim(),
      senderId: input.author.userId,
      senderUsername: input.author.username,
      senderRole: input.author.role,
      replyToMessageId: replyTarget?._id ?? null,
      deletedAt: null,
      deletedByUserId: null,
    });
    return this.serializeMessage(
      created.toObject<MessageLean>(),
      replyTarget ? new Map([[this.asIdString(replyTarget._id)!, replyTarget]]) : undefined,
    );
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
      deletedAt: null,
    };
    const before = options?.before?.trim();
    if (before) {
      if (!Types.ObjectId.isValid(before)) {
        throw new BadRequestException('Invalid before cursor');
      }
      filter._id = { $lt: new Types.ObjectId(before) };
    }
    const messages = await this.messageModel
      .find(filter)
      .sort({ createdAt: -1 })
      .limit(limit)
      .lean<MessageLean[]>()
      .exec();
    return this.enrichMessages(messages);
  }

  async deleteMessage(
    userId: string,
    messageId: string,
  ): Promise<{ messageId: string; roomId: string }> {
    if (!Types.ObjectId.isValid(messageId)) {
      throw new BadRequestException('Invalid message id');
    }
    await this.assertUserMayUseChat(userId);
    const actor = await this.usersService.findById(userId);
    if (!actor) {
      throw new ForbiddenException('User not found');
    }
    const message = await this.messageModel.findById(messageId).exec();
    if (!message) {
      throw new NotFoundException('Message not found');
    }
    if (message.allianceId !== actor.allianceName) {
      throw new ForbiddenException('Message is not available for your alliance');
    }
    const mayDelete =
      message.senderId === userId || actor.role === AllianceRole.R5;
    if (!mayDelete) {
      throw new ForbiddenException('You may only delete your own messages');
    }
    const roomId = this.asIdString(message.roomId)!;
    const res = await this.messageModel
      .deleteOne({
        _id: new Types.ObjectId(messageId),
        allianceId: actor.allianceName,
      })
      .exec();
    if (res.deletedCount !== 1) {
      throw new NotFoundException('Message not found');
    }
    await this.messageModel
      .updateMany(
        {
          allianceId: actor.allianceName,
          roomId: message.roomId,
          replyToMessageId: new Types.ObjectId(messageId),
        },
        { $set: { replyToMessageId: null } },
      )
      .exec();
    return { messageId, roomId };
  }
}
