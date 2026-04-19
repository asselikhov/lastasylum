import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/global-chat-alliance-id';
import { UsersService } from '../users/users.service';
import { PushNotificationsService } from '../push/push-notifications.service';
import { ChatGateway } from './chat.gateway';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatService } from './chat.service';
import { CreateChatRoomDto } from './dto/create-chat-room.dto';
import { CreateMessageDto } from './dto/create-message.dto';
import { UpdateChatRoomDto } from './dto/update-chat-room.dto';

type RequestUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

@Controller('chat')
@UseGuards(JwtAuthGuard, RolesGuard)
export class ChatController {
  constructor(
    private readonly chatService: ChatService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly chatGateway: ChatGateway,
    private readonly usersService: UsersService,
    private readonly pushNotifications: PushNotificationsService,
  ) {}

  @Get('rooms')
  @Roles(AllianceRole.R2)
  async listRooms(@Req() req: { user: RequestUser }) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.chatRoomsService.listRoomsVisibleToUser(user.allianceName);
  }

  @Post('rooms')
  @Roles(AllianceRole.R5)
  async createRoom(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateChatRoomDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.chatRoomsService.createRoom(
      user.allianceName,
      dto.title,
      dto.sortOrder,
    );
  }

  @Patch('rooms/:roomId')
  @Roles(AllianceRole.R5)
  async updateRoom(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
    @Body() dto: UpdateChatRoomDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.chatRoomsService.updateRoom(roomId, user.allianceName, {
      title: dto.title,
      sortOrder: dto.sortOrder,
      archived: dto.archived,
    });
  }

  @Delete('rooms/:roomId')
  @Roles(AllianceRole.R5)
  async deleteRoom(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    await this.chatRoomsService.deleteRoom(roomId, user.allianceName);
    return { success: true };
  }

  @Get('messages')
  @Roles(AllianceRole.R2)
  async getRecentMessages(
    @Req() req: { user: RequestUser },
    @Query('roomId') roomId?: string,
    @Query('before') before?: string,
    @Query('limit') limitRaw?: string,
  ) {
    if (!roomId?.trim()) {
      throw new BadRequestException('roomId query parameter is required');
    }
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const parsed = limitRaw ? Number.parseInt(limitRaw, 10) : undefined;
    const limit = Number.isFinite(parsed) ? parsed : undefined;
    return this.chatService.getRecentMessages(req.user.userId, roomId, {
      before,
      limit,
    });
  }

  @Post('messages')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 8, ttl: 10_000 } })
  async createMessage(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateMessageDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const message = await this.chatService.createMessage({
      roomId: dto.roomId,
      text: dto.text,
      replyToMessageId: dto.replyToMessageId,
      author: req.user,
    });
    this.chatGateway.broadcastNewMessage(dto.roomId, message);
    const authorUser = await this.usersService.findById(req.user.userId);
    if (authorUser && message.allianceId !== GLOBAL_CHAT_ALLIANCE_ID) {
      const preview =
        dto.text.trim().length > 140
          ? `${dto.text.trim().slice(0, 137)}...`
          : dto.text.trim();
      const messageId =
        typeof (message as { _id?: unknown })._id === 'string'
          ? (message as { _id: string })._id
          : typeof (message as { _id?: unknown })._id === 'object' &&
              (message as { _id?: { toString?: () => string } })._id != null
            ? (message as { _id: { toString: () => string } })._id.toString()
            : '';
      void this.pushNotifications
        .notifyAllianceChatMessage({
          allianceId: authorUser.allianceName,
          excludeUserId: req.user.userId,
          title: `${req.user.username}`,
          body: preview,
          data: {
            type: 'chat_message',
            roomId: dto.roomId,
            messageId,
          },
        })
        .catch(() => undefined);
    }
    return message;
  }

  @Delete('messages/:messageId')
  @Roles(AllianceRole.R2)
  async deleteMessage(
    @Req() req: { user: RequestUser },
    @Param('messageId') messageId: string,
  ) {
    const deleted = await this.chatService.deleteMessage(req.user.userId, messageId);
    this.chatGateway.broadcastMessageDeleted(deleted.roomId, {
      messageId: deleted.messageId,
      roomId: deleted.roomId,
    });
    return deleted;
  }
}
