import {
  BadRequestException,
  Body,
  Controller,
  ForbiddenException,
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
import { resolveChatAllianceScope } from './chat-alliance-scope';
import { UsersService } from '../users/users.service';
import { PushNotificationsService } from '../push/push-notifications.service';
import { ChatGateway } from './chat.gateway';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatService } from './chat.service';
import {
  ChatAttachmentsService,
} from './chat-attachments.service';
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

class MarkReadDto {
  messageId: string;
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
    return this.chatRoomsService.listRoomsVisibleToUser(user);
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
      resolveChatAllianceScope(user),
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
    return this.chatRoomsService.updateRoom(
      roomId,
      resolveChatAllianceScope(user),
      {
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
    await this.chatRoomsService.deleteRoom(
      roomId,
      resolveChatAllianceScope(user),
    );
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
    const anyChat = this.chatService as unknown as {
      assertRoomForUser: (
        userId: string,
        roomId: string,
      ) => Promise<{ allianceId: string; roomObjectId: Types.ObjectId }>;
    };
    const { allianceId, roomObjectId } = await anyChat.assertRoomForUser(
      req.user.userId,
      dto.roomId,
    );
    const resolvedAttachments = await this.attachmentsService.resolveForRoom({
      allianceId,
      roomObjectId,
      attachmentIds,
    });
    const message = await this.chatService.createMessage({
      roomId: dto.roomId,
      text: dto.text ?? '',
      replyToMessageId: dto.replyToMessageId,
      author: req.user,
      attachments: resolvedAttachments,
    });
    this.chatGateway.broadcastNewMessage(dto.roomId, message);
    const authorUser = await this.usersService.findById(req.user.userId);
    const messageId =
      typeof (message as { _id?: unknown })._id === 'string'
        ? (message as { _id: string })._id
        : typeof (message as { _id?: unknown })._id === 'object' &&
            (message as { _id?: { toString?: () => string } })._id != null
          ? (message as { _id: { toString: () => string } })._id.toString()
          : '';
    if (
      authorUser &&
      message.allianceId !== GLOBAL_CHAT_ALLIANCE_ID &&
      dto.excavationAlert === true
    ) {
      const excavationBody = dto.text?.trim() ?? '';
      void this.pushNotifications
        .notifyExcavationAlert({
          allianceId: message.allianceId,
          excludeUserId: req.user.userId,
          senderName: req.user.username,
          body: excavationBody,
          data: {
            roomId: dto.roomId,
            messageId,
          },
        })
        .catch(() => undefined);
    } else if (authorUser && message.allianceId !== GLOBAL_CHAT_ALLIANCE_ID) {
      const preview = dto.text?.trim()
        ? formatChatPushBody(dto.text)
        : resolvedAttachments.length > 0
          ? resolvedAttachments.some((a) => a.kind === 'file')
            ? resolvedAttachments.find((a) => a.kind === 'file')?.filename?.trim() ||
              'Файл'
            : 'Фото'
          : '';
      void this.pushNotifications
        .notifyAllianceChatMessage({
          allianceId: message.allianceId,
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
      limits: { fileSize: 120 * 1024 * 1024 },
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

    const mimeType = (file.mimetype ?? '').trim() || 'application/octet-stream';
    const originalName = (file.originalname ?? '').trim();

    if (mimeType.startsWith('image/')) {
      return this.attachmentsService.uploadImage({
        buffer: file.buffer,
        filename: originalName,
        mimeType,
        size: file.size,
        allianceId,
        roomId: roomObjectId,
        uploaderUserId: req.user.userId,
      });
    }

    if (ChatAttachmentsService.isApkUpload(mimeType, originalName)) {
      if (req.user.role !== AllianceRole.R5) {
        throw new ForbiddenException('Only alliance admins (R5) may upload APK files');
      }
      return this.attachmentsService.uploadFile({
        buffer: file.buffer,
        filename: originalName || 'update.apk',
        mimeType,
        size: file.size,
        allianceId,
        roomId: roomObjectId,
        uploaderUserId: req.user.userId,
      });
    }

    throw new BadRequestException(
      'Unsupported file type (images or APK for R5 admins only)',
    );
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
    const downloadName =
      download.filename?.trim() ||
      (download.kind === 'file' ? `squadrelay-${fileId}.apk` : `${fileId}`);
    if (download.kind === 'file') {
      res.setHeader(
        'Content-Disposition',
        `attachment; filename="${encodeURIComponent(downloadName)}"`,
      );
    }
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

  @Post('rooms/:roomId/read')
  @Roles(AllianceRole.R2)
  async markRoomRead(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
    @Body() dto: MarkReadDto,
  ) {
    const messageId = dto?.messageId?.trim() ?? '';
    if (!messageId) throw new BadRequestException('messageId is required');
    const result = await this.chatService.markRoomRead({
      userId: req.user.userId,
      roomId: roomId.trim(),
      messageId,
    });
    this.chatGateway.server?.to(`chat:${result.roomId}`).emit('room:read', result);
    return { success: true };
  }
}
