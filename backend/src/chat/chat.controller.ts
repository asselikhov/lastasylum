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
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { isAppAdminRole } from '../common/alliance-role.util';
import {
  assertUploadSizeWithinLimit,
  FORUM_APK_MAX_UPLOAD_BYTES,
  FREE_TIER_MAX_UPLOAD_BYTES,
  withUploadSlot,
} from '../common/upload-concurrency';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/chat-room-constants';
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
import { UpdateChatRoomDto } from './dto/update-chat-room.dto';
import { UploadChatAttachmentDto } from './dto/upload-chat-attachment.dto';
import { ToggleReactionDto } from './dto/toggle-reaction.dto';
import type { Response } from 'express';
import { Types } from 'mongoose';

class EditMessageDto {
  text: string;
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
  @Roles(AllianceRole.MEMBER)
  async listRooms(@Req() req: { user: RequestUser }) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    const rooms = await this.chatRoomsService.listRoomsVisibleToUser(user);
    const roomIds = rooms
      .map((r) => {
        const id = (r as { _id?: Types.ObjectId })._id;
        return id?.toString() ?? '';
      })
      .filter(Boolean);
    const unreadMap = await this.chatService.countUnreadByRoomIds(
      req.user.userId,
      roomIds,
    );
    const lastReadMap = await this.chatService.getLastReadMessageIdsByRoomIds(
      req.user.userId,
      roomIds,
    );
    return rooms.map((r) => {
      const id = (r as { _id: Types.ObjectId })._id.toString();
      return {
        ...r,
        unreadCount: unreadMap.get(id) ?? 0,
        lastReadMessageId: lastReadMap.get(id) ?? null,
      };
    });
  }

  @Post('rooms')
  @Roles(AllianceRole.ADMIN)
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
  @Roles(AllianceRole.ADMIN)
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
  @Roles(AllianceRole.ADMIN)
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
  @Roles(AllianceRole.MEMBER)
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
  @Roles(AllianceRole.MEMBER)
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
    this.chatGateway.broadcastNewMessageWithOverlayFanout(
      dto.roomId,
      message,
      req.user.userId,
    );
    void this.chatGateway.notifyRoomUnreadAfterNewMessage(
      dto.roomId,
      req.user.userId,
    );
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
    }
    // Обычные сообщения альянса не шлём push: в игре — оверлей/сокет; вне игры — только
    // excavation_alert (notifyExcavationAlert) со звуком, чтобы не отвлекать от жизни вне рейда.
    return message;
  }

  @Post('attachments')
  @Roles(AllianceRole.MEMBER)
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

    return withUploadSlot(async () => {
      if (mimeType.startsWith('image/')) {
        assertUploadSizeWithinLimit(file.size, FREE_TIER_MAX_UPLOAD_BYTES);
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
        if (!isAppAdminRole(req.user.role)) {
          throw new ForbiddenException(
            'Only app administrators may upload APK files',
          );
        }
        assertUploadSizeWithinLimit(file.size, FORUM_APK_MAX_UPLOAD_BYTES, 'APK');
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
        'Unsupported file type (images or APK for app admins only)',
      );
    });
  }

  @Get('attachments/:fileId')
  @Roles(AllianceRole.MEMBER)
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
  @Roles(AllianceRole.MEMBER)
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
  @Roles(AllianceRole.MEMBER)
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
  @Roles(AllianceRole.MEMBER)
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
  @Roles(AllianceRole.MEMBER)
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
    this.chatGateway.broadcastNewMessageWithOverlayFanout(
      roomId,
      forwarded,
      req.user.userId,
    );
    void this.chatGateway.notifyRoomUnreadAfterNewMessage(
      roomId,
      req.user.userId,
    );
    return forwarded;
  }

  @Post('rooms/:roomId/read')
  @Roles(AllianceRole.MEMBER)
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
    void this.chatGateway.emitUnreadToUser(req.user.userId, result.roomId);
    return {
      success: true,
      unreadCount: result.unreadCount ?? 0,
    };
  }
}
