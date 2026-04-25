import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Header,
  Param,
  Patch,
  Post,
  Query,
  Req,
  Res,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
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
import { ChatAttachmentsService } from './chat-attachments.service';
import { CreateChatRoomDto } from './dto/create-chat-room.dto';
import { CreateMessageDto } from './dto/create-message.dto';
import { formatChatPushBody } from './zlobyaka-stickers.const';
import { UpdateChatRoomDto } from './dto/update-chat-room.dto';
import { UploadChatAttachmentDto } from './dto/upload-chat-attachment.dto';
import type { Response } from 'express';
import { Types } from 'mongoose';

class EditMessageDto {
  text: string;
}

class ToggleReactionDto {
  emoji: string;
}

class ForwardMessageDto {
  roomId: string;
}

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
    private readonly attachmentsService: ChatAttachmentsService,
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
    const attachmentIds =
      dto.attachments?.slice(0, 8).filter((id) => Types.ObjectId.isValid(id)) ??
      [];
    const message = await this.chatService.createMessage({
      roomId: dto.roomId,
      text: dto.text ?? '',
      replyToMessageId: dto.replyToMessageId,
      author: req.user,
      attachments: attachmentIds.map((id) => ({
        kind: 'image' as const,
        fileId: new Types.ObjectId(id),
        // mime/size are taken from GridFS metadata at read time; stored values are best-effort.
        mimeType: 'image/*',
        size: 0,
      })),
    });
    this.chatGateway.broadcastNewMessage(dto.roomId, message);
    const authorUser = await this.usersService.findById(req.user.userId);
    if (authorUser && message.allianceId !== GLOBAL_CHAT_ALLIANCE_ID) {
      const preview = dto.text?.trim()
        ? formatChatPushBody(dto.text)
        : attachmentIds.length > 0
          ? 'Фото'
          : '';
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

  @Post('attachments')
  @Roles(AllianceRole.R2)
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: 8 * 1024 * 1024 },
    }),
  )
  async uploadAttachment(
    @Req() req: { user: RequestUser },
    @UploadedFile() file: Express.Multer.File | undefined,
    @Body() dto: UploadChatAttachmentDto,
  ) {
    const roomId = dto.roomId.trim();
    if (!roomId) throw new BadRequestException('roomId is required');
    if (!file) throw new BadRequestException('file is required');
    if (!file.buffer?.length) {
      throw new BadRequestException(
        'file buffer is empty (multipart parse failed?)',
      );
    }
    await this.chatService.assertUserMayUseChat(req.user.userId);

    // Use the same access logic as message send: user must be able to join this room.
    const anyChat = this.chatService as unknown as {
      assertRoomForUser: (
        userId: string,
        roomId: string,
      ) => Promise<{ allianceId: string; roomObjectId: Types.ObjectId }>;
    };
    const { allianceId, roomObjectId } = await anyChat.assertRoomForUser(
      req.user.userId,
      roomId,
    );

    return this.attachmentsService.uploadImage({
      buffer: file.buffer,
      filename: file.originalname,
      mimeType: file.mimetype,
      size: file.size,
      allianceId,
      roomId: roomObjectId,
      uploaderUserId: req.user.userId,
    });
  }

  @Get('attachments/:fileId')
  @Roles(AllianceRole.R2)
  @Header('Cache-Control', 'private, max-age=3600')
  async getAttachment(
    @Req() req: { user: RequestUser },
    @Param('fileId') fileId: string,
    @Res() res: Response,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const doc = await this.attachmentsService.findAttachment(fileId);
    if (!doc) {
      res.status(404).end();
      return;
    }

    const anyChat = this.chatService as unknown as {
      assertRoomForUser: (
        userId: string,
        roomId: string,
      ) => Promise<{ allianceId: string; roomObjectId: Types.ObjectId }>;
    };
    await anyChat.assertRoomForUser(req.user.userId, doc.roomId.toString());

    res.setHeader('Content-Type', doc.mimeType ?? 'application/octet-stream');
    const download = await this.attachmentsService.openR2Download(fileId);
    download.stream.on('error', () => res.status(404).end());
    download.stream.pipe(res);
  }

  @Delete('messages/:messageId')
  @Roles(AllianceRole.R2)
  async deleteMessage(
    @Req() req: { user: RequestUser },
    @Param('messageId') messageId: string,
  ) {
    const deleted = await this.chatService.deleteMessage(
      req.user.userId,
      messageId,
    );
    this.chatGateway.broadcastMessageDeleted(deleted.roomId, {
      messageId: deleted.messageId,
      roomId: deleted.roomId,
    });
    return deleted;
  }

  @Patch('messages/:messageId')
  @Roles(AllianceRole.R2)
  async editMessage(
    @Req() req: { user: RequestUser },
    @Param('messageId') messageId: string,
    @Body() dto: EditMessageDto,
  ) {
    const edited = await this.chatService.editMessage(
      req.user.userId,
      messageId,
      dto?.text ?? '',
    );
    this.chatGateway.server
      ?.to(`chat:${edited.roomId}`)
      .emit('message:edited', edited);
    return edited;
  }

  @Post('messages/:messageId/reactions')
  @Roles(AllianceRole.R2)
  async toggleReaction(
    @Req() req: { user: RequestUser },
    @Param('messageId') messageId: string,
    @Body() dto: ToggleReactionDto,
  ) {
    const updated = await this.chatService.toggleReaction(
      req.user.userId,
      messageId,
      dto?.emoji ?? '',
    );
    this.chatGateway.server
      ?.to(`chat:${updated.roomId}`)
      .emit('message:reaction', updated);
    return updated;
  }

  @Post('messages/:messageId/forward')
  @Roles(AllianceRole.R2)
  async forwardMessage(
    @Req() req: { user: RequestUser },
    @Param('messageId') messageId: string,
    @Body() dto: ForwardMessageDto,
  ) {
    const roomId = dto?.roomId?.trim() ?? '';
    if (!roomId) {
      throw new BadRequestException('roomId is required');
    }
    const forwarded = await this.chatService.forwardMessage(
      req.user.userId,
      roomId,
      messageId,
    );
    this.chatGateway.broadcastNewMessage(roomId, forwarded);
    return forwarded;
  }
}
