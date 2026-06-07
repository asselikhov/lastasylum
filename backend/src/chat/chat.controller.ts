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
  Put,
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
import { resolveGameEventId } from '../game-events/game-event-catalog';
import { RolesGuard } from '../common/guards/roles.guard';
import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/chat-room-constants';
import { resolveChatAllianceScope } from './chat-alliance-scope';
import { UsersService } from '../users/users.service';
import { ChatGateway } from './chat.gateway';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatService } from './chat.service';
import { ChatAttachmentsService } from './chat-attachments.service';
import { OverlayReactionLogService } from './overlay-reaction-log.service';
import { CreateChatRoomDto } from './dto/create-chat-room.dto';
import { CreateMessageDto } from './dto/create-message.dto';
import { UpdateChatRoomDto } from './dto/update-chat-room.dto';
import { UploadChatAttachmentDto } from './dto/upload-chat-attachment.dto';
import { ToggleReactionDto } from './dto/toggle-reaction.dto';
import { MarkRoomReadDto } from './dto/mark-room-read.dto';
import { PinRoomMessageDto } from './dto/pin-room-message.dto';
import { EditMessageDto } from './dto/edit-message.dto';
import { ForwardMessageDto } from './dto/forward-message.dto';
import { OverlayReactionReadCursorDto } from './dto/overlay-reaction-read-cursor.dto';
import { ToggleOverlayReactionLogReactionDto } from './dto/toggle-overlay-reaction-log-reaction.dto';
import type { Response } from 'express';
import { Types } from 'mongoose';

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
    private readonly attachmentsService: ChatAttachmentsService,
    private readonly overlayReactionLogService: OverlayReactionLogService,
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
    const withUnread = rooms.map((r) => {
      const id = (r as { _id: Types.ObjectId })._id.toString();
      return {
        ...r,
        unreadCount: unreadMap.get(id) ?? 0,
        lastReadMessageId: lastReadMap.get(id) ?? null,
      };
    });
    return this.chatService.attachPinStateToRooms(withUnread);
  }

  @Put('rooms/:roomId/pin')
  @Roles(AllianceRole.MEMBER)
  async pinRoomMessage(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
    @Body() dto: PinRoomMessageDto,
  ) {
    const raw = dto?.messageId;
    const messageId =
      raw === null || raw === undefined
        ? null
        : typeof raw === 'string'
          ? raw
          : null;
    const { room, pinChanged } = await this.chatService.setRoomPinnedMessage(
      req.user.userId,
      roomId,
      messageId,
    );
    this.chatGateway.broadcastRoomPinChanged(pinChanged);
    return room;
  }

  @Delete('rooms/:roomId/pin/:messageId')
  @Roles(AllianceRole.MEMBER)
  async unpinOneRoomMessage(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
    @Param('messageId') messageId: string,
  ) {
    const { room, pinChanged } = await this.chatService.unpinOneRoomMessage(
      req.user.userId,
      roomId,
      messageId,
    );
    this.chatGateway.broadcastRoomPinChanged(pinChanged);
    return room;
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
      },
    );
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

  @Get('sync-state')
  @Roles(AllianceRole.MEMBER)
  async getSyncState(@Req() req: { user: RequestUser }) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    return this.chatService.getChatSyncState();
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
    const authorUser = await this.chatService.assertUserMayUseChat(
      req.user.userId,
    );
    const parsed = limitRaw ? Number.parseInt(limitRaw, 10) : undefined;
    const limit = Number.isFinite(parsed) ? parsed : undefined;
    return this.chatService.getRecentMessages(req.user.userId, roomId, {
      before,
      limit,
      userHint: authorUser,
    });
  }

  @Post('messages')
  @Roles(AllianceRole.MEMBER)
  async createMessage(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateMessageDto,
  ) {
    const authorUser = await this.chatService.assertUserMayUseChat(
      req.user.userId,
    );
    const { allianceId, roomObjectId } = await this.chatService.resolveRoomAccess(
      authorUser,
      dto.roomId,
    );
    const attachmentIds =
      dto.attachments?.slice(0, 8).filter((id) => Types.ObjectId.isValid(id)) ??
      [];
    const resolvedAttachments = await this.attachmentsService.resolveForRoom({
      allianceId,
      roomObjectId,
      attachmentIds,
    });
    const { message, created } = await this.chatService.createMessage({
      roomId: dto.roomId,
      text: dto.text ?? '',
      replyToMessageId: dto.replyToMessageId,
      author: req.user,
      attachments: resolvedAttachments,
      clientMessageId: dto.clientMessageId,
      authorUser,
      roomContext: { allianceId, roomObjectId },
    });
    if (created) {
      const messageId =
        typeof (message as { _id?: unknown })._id === 'string'
          ? (message as { _id: string })._id
          : typeof (message as { _id?: unknown })._id === 'object' &&
              (message as { _id?: { toString?: () => string } })._id != null
            ? (message as { _id: { toString: () => string } })._id.toString()
            : '';
      const gameEventId = resolveGameEventId(
        dto.gameEventAlert,
        dto.excavationAlert,
      );
      this.chatGateway.afterMessageCreated({
        roomId: dto.roomId,
        message,
        senderUserId: req.user.userId,
        gameEventId,
        gameEventText: dto.text?.trim() ?? '',
        messageAllianceId: message.allianceId,
        messageId,
        senderName: req.user.username,
      });
    }
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
        assertUploadSizeWithinLimit(
          file.size,
          FORUM_APK_MAX_UPLOAD_BYTES,
          'APK',
        );
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
    if (deleted.pinChanged) {
      this.chatGateway.broadcastRoomPinChanged(deleted.pinChanged);
    }
    void this.chatGateway.notifyRoomUnreadAfterNewMessage(deleted.roomId, '');
    return deleted;
  }

  @Patch('messages/:messageId')
  @Roles(AllianceRole.MEMBER)
  async editMessage(
    @Req() req: { user: RequestUser },
    @Param('messageId') messageId: string,
    @Body() dto: EditMessageDto,
  ) {
    const { message: edited, pinChanged } = await this.chatService.editMessage(
      req.user.userId,
      messageId,
      dto?.text ?? '',
    );
    this.chatGateway.broadcastChatRoomEvent(
      edited.roomId,
      'message:edited',
      edited,
      req.user.userId,
    );
    if (pinChanged) {
      this.chatGateway.broadcastRoomPinChanged(pinChanged);
    }
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
    const broadcast =
      await this.chatService.getReactionBroadcastPayload(messageId);
    if (broadcast) {
      this.chatGateway.broadcastChatRoomEvent(
        broadcast.roomId,
        'message:reaction',
        broadcast,
        req.user.userId,
      );
    }
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
    await this.chatGateway.notifyRoomUnreadAfterNewMessage(
      roomId,
      req.user.userId,
    );
    this.chatGateway.broadcastNewMessageWithOverlayFanout(
      roomId,
      forwarded,
      req.user.userId,
    );
    return forwarded;
  }

  @Post('rooms/:roomId/clear-history')
  @Roles(AllianceRole.MEMBER)
  async clearRoomHistory(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
  ) {
    const rid = roomId?.trim() ?? '';
    if (!rid) throw new BadRequestException('roomId is required');
    return this.chatService.clearRoomHistoryForUser(req.user.userId, rid);
  }

  @Get('rooms/:roomId/peer-read-cursor')
  @Roles(AllianceRole.MEMBER)
  async getPeerReadCursor(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
  ) {
    const rid = roomId?.trim() ?? '';
    if (!rid) throw new BadRequestException('roomId is required');
    const messageId = await this.chatService.getPeerReadUptoMessageId(
      req.user.userId,
      rid,
    );
    return { messageId };
  }

  @Post('rooms/:roomId/read')
  @Roles(AllianceRole.MEMBER)
  async markRoomRead(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
    @Body() dto: MarkRoomReadDto,
  ) {
    const messageId = dto?.messageId?.trim() ?? '';
    if (!messageId) throw new BadRequestException('messageId is required');
    const result = await this.chatService.markRoomRead({
      userId: req.user.userId,
      roomId: roomId.trim(),
      messageId,
    });
    this.chatGateway.broadcastChatRoomEvent(
      result.roomId,
      'room:read',
      result,
      result.userId,
    );
    void this.chatGateway.emitUnreadToUser(req.user.userId, result.roomId);
    return {
      success: true,
      unreadCount: result.unreadCount ?? 0,
    };
  }

  @Get('overlay-reactions')
  @Roles(AllianceRole.MEMBER)
  async listOverlayReactions(
    @Req() req: { user: RequestUser },
    @Query('before') before?: string,
    @Query('limit') limit?: string,
  ) {
    const parsedLimit = limit ? parseInt(limit, 10) : undefined;
    return this.overlayReactionLogService.listForViewer(req.user.userId, {
      before: before?.trim() || undefined,
      limit: Number.isFinite(parsedLimit) ? parsedLimit : undefined,
    });
  }

  @Get('overlay-reactions/read-cursor')
  @Roles(AllianceRole.MEMBER)
  async getOverlayReactionReadCursor(@Req() req: { user: RequestUser }) {
    return this.overlayReactionLogService.getReadCursor(req.user.userId);
  }

  @Patch('overlay-reactions/read-cursor')
  @Roles(AllianceRole.MEMBER)
  async advanceOverlayReactionReadCursor(
    @Req() req: { user: RequestUser },
    @Body() dto: OverlayReactionReadCursorDto,
  ) {
    const lastSeenLogId = dto?.lastSeenLogId?.trim() ?? '';
    if (!lastSeenLogId) {
      throw new BadRequestException('lastSeenLogId is required');
    }
    return this.overlayReactionLogService.advanceReadCursor(
      req.user.userId,
      lastSeenLogId,
    );
  }

  @Patch('overlay-reactions/:logId/reactions')
  @Roles(AllianceRole.MEMBER)
  async toggleOverlayReactionLogReaction(
    @Req() req: { user: RequestUser },
    @Param('logId') logId: string,
    @Body() dto: ToggleOverlayReactionLogReactionDto,
  ) {
    const emoji = dto?.emoji?.trim() ?? '';
    if (!emoji) {
      throw new BadRequestException('emoji is required');
    }
    const result = await this.overlayReactionLogService.toggleLogEntryReaction(
      req.user.userId,
      logId,
      emoji,
    );
    this.chatGateway.emitOverlayReactionLogReaction(
      result.entry,
      result.recipientUserIds,
    );
    return result.entry;
  }
}
