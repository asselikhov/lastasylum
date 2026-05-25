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
import type { Response } from 'express';
import { Types } from 'mongoose';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import {
  assertUploadSizeWithinLimit,
  FORUM_APK_MAX_UPLOAD_BYTES,
  FREE_TIER_MAX_UPLOAD_BYTES,
  withUploadSlot,
} from '../common/upload-concurrency';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { AddTeamMemberDto } from './dto/add-team-member.dto';
import { CreatePlayerTeamDto } from './dto/create-player-team.dto';
import { CreateTeamNewsDto } from './dto/create-team-news.dto';
import { UpdatePlayerTeamDisplayNameDto } from './dto/update-player-team-display.dto';
import { UpdateSquadMemberRoleDto } from './dto/update-squad-member-role.dto';
import { UpdateTeamNewsDto } from './dto/update-team-news.dto';
import { VoteTeamNewsDto } from './dto/vote-team-news.dto';
import {
  AdvanceTeamNewsReadCursorDto,
  TeamNewsReadCursorDto,
} from './dto/team-news-read-cursor.dto';
import { TeamForumGateway } from './team-forum.gateway';
import {
  CreateTeamForumMessageDto,
  CreateTeamForumTopicDto,
  BulkDeleteTeamForumMessagesDto,
  MarkTeamForumTopicReadDto,
  UpdateTeamForumMessageDto,
  UpdateTeamForumTopicDto,
} from './dto/team-forum.dto';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { TeamNewsService } from './team-news.service';
import { TeamForumService } from './team-forum.service';
import { TeamsService } from './teams.service';

type RequestUser = {
  userId: string;
};

@Controller('teams')
@UseGuards(JwtAuthGuard, RolesGuard)
export class TeamsController {
  constructor(
    private readonly teams: TeamsService,
    private readonly teamNews: TeamNewsService,
    private readonly teamNewsAttachments: TeamNewsAttachmentsService,
    private readonly teamForum: TeamForumService,
    private readonly teamForumGateway: TeamForumGateway,
  ) {}

  @Post()
  @Roles(AllianceRole.MEMBER)
  createTeam(
    @Req() req: { user: RequestUser },
    @Body() dto: CreatePlayerTeamDto,
  ) {
    return this.teams.createTeam(req.user.userId, dto.displayName, dto.tag);
  }

  @Get('search')
  @Roles(AllianceRole.MEMBER)
  search(
    @Req() req: { user: RequestUser },
    @Query('q') q: string,
    @Query('limit') limit?: string,
  ) {
    const lim = limit != null ? Number.parseInt(limit, 10) : 20;
    return this.teams.searchTeams(
      q ?? '',
      req.user.userId,
      Number.isFinite(lim) ? lim : 20,
    );
  }

  @Get('me/join-requests')
  @Roles(AllianceRole.MEMBER)
  myJoinRequests(@Req() req: { user: RequestUser }) {
    return this.teams.listPendingJoinRequestsForLeader(req.user.userId);
  }

  @Post('join-requests/:requestId/accept')
  @Roles(AllianceRole.MEMBER)
  acceptRequest(
    @Req() req: { user: RequestUser },
    @Param('requestId') requestId: string,
  ) {
    return this.teams.acceptJoinRequest(requestId, req.user.userId);
  }

  @Post('join-requests/:requestId/reject')
  @Roles(AllianceRole.MEMBER)
  rejectRequest(
    @Req() req: { user: RequestUser },
    @Param('requestId') requestId: string,
  ) {
    return this.teams.rejectJoinRequest(requestId, req.user.userId);
  }

  @Get(':teamId/news/attachments/:fileId')
  @Roles(AllianceRole.MEMBER)
  @Header('Cache-Control', 'private, max-age=3600')
  async getNewsAttachment(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('fileId') fileId: string,
    @Res() res: Response,
  ) {
    await this.teams.getTeamIfMemberOrThrow(teamId, req.user.userId);
    const dl = await this.teamNewsAttachments.openDownloadForTeam(
      new Types.ObjectId(teamId),
      fileId,
    );
    res.setHeader('Content-Type', dl.mimeType);
    dl.stream.on('error', () => res.status(404).end());
    dl.stream.pipe(res);
  }

  @Post(':teamId/news/attachments')
  @Roles(AllianceRole.MEMBER)
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: 8 * 1024 * 1024 },
    }),
  )
  async uploadNewsImage(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @UploadedFile() file: Express.Multer.File | undefined,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('file is required');
    }
    await this.teamNews.assertMayUploadNewsImage(teamId, req.user.userId);
    return withUploadSlot(async () => {
      assertUploadSizeWithinLimit(file.size, FREE_TIER_MAX_UPLOAD_BYTES);
      return this.teamNewsAttachments.uploadImage({
        teamId: new Types.ObjectId(teamId),
        uploaderUserId: req.user.userId,
        buffer: file.buffer,
        mimeType: file.mimetype,
        size: file.size,
      });
    });
  }

  @Post(':teamId/forum/attachments')
  @Roles(AllianceRole.MEMBER)
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: 8 * 1024 * 1024 },
    }),
  )
  async uploadForumImage(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @UploadedFile() file: Express.Multer.File | undefined,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('file is required');
    }
    await this.teams.getTeamIfMemberOrThrow(teamId, req.user.userId);
    return withUploadSlot(async () => {
      assertUploadSizeWithinLimit(file.size, FREE_TIER_MAX_UPLOAD_BYTES);
      return this.teamNewsAttachments.uploadImage({
        teamId: new Types.ObjectId(teamId),
        uploaderUserId: req.user.userId,
        buffer: file.buffer,
        mimeType: file.mimetype,
        size: file.size,
      });
    });
  }

  @Post(':teamId/forum/attachments/file')
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: 120 * 1024 * 1024 },
    }),
  )
  async uploadForumFile(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @UploadedFile() file: Express.Multer.File | undefined,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('file is required');
    }
    await this.teams.assertSquadOfficerOrThrow(teamId, req.user.userId);
    const name =
      typeof file.originalname === 'string' && file.originalname.trim()
        ? file.originalname.trim()
        : 'update.apk';
    return withUploadSlot(async () => {
      assertUploadSizeWithinLimit(file.size, FORUM_APK_MAX_UPLOAD_BYTES, 'APK');
      return this.teamNewsAttachments.uploadForumFile({
        teamId: new Types.ObjectId(teamId),
        uploaderUserId: req.user.userId,
        buffer: file.buffer,
        mimeType: file.mimetype,
        size: file.size,
        filename: name,
      });
    });
  }

  @Get(':teamId/ingame-online')
  @Roles(AllianceRole.MEMBER)
  teamIngameOnline(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
  ) {
    return this.teams.getTeamOverlayPresence(teamId, req.user.userId);
  }

  @Get(':teamId/inbox-badges')
  @Roles(AllianceRole.MEMBER)
  async teamInboxBadges(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Query('newsAfter') newsAfter?: string,
  ) {
    if (newsAfter?.trim()) {
      await this.teamNews.adoptClientLastSeen(
        teamId,
        req.user.userId,
        newsAfter,
      );
    }
    const [forumUnread, newsUnread] = await Promise.all([
      this.teamForum.sumUnreadMessages(teamId, req.user.userId),
      this.teamNews.countUnread(teamId, req.user.userId),
    ]);
    return { forumUnread, newsUnread };
  }

  @Get(':teamId/news/read-cursor')
  @Roles(AllianceRole.MEMBER)
  teamNewsReadCursor(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
  ) {
    return this.teamNews.getReadCursor(teamId, req.user.userId);
  }

  @Put(':teamId/news/read-cursor')
  @Roles(AllianceRole.MEMBER)
  advanceTeamNewsReadCursor(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Body() body: AdvanceTeamNewsReadCursorDto,
  ) {
    return this.teamNews.advanceReadCursor(
      teamId,
      req.user.userId,
      body.createdAt,
    );
  }

  @Get(':teamId/news')
  @Roles(AllianceRole.MEMBER)
  listNews(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Query('cursor') cursor?: string,
    @Query('limit') limit?: string,
  ) {
    const lim = limit != null ? Number.parseInt(limit, 10) : 20;
    return this.teamNews.list(
      teamId,
      req.user.userId,
      cursor,
      Number.isFinite(lim) ? lim : 20,
    );
  }

  @Post(':teamId/news')
  @Roles(AllianceRole.MEMBER)
  createNews(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Body() dto: CreateTeamNewsDto,
  ) {
    return this.teamNews.create(teamId, req.user.userId, dto);
  }

  @Get(':teamId/news/:newsId')
  @Roles(AllianceRole.MEMBER)
  getNews(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('newsId') newsId: string,
  ) {
    return this.teamNews.getOne(teamId, newsId, req.user.userId);
  }

  @Patch(':teamId/news/:newsId')
  @Roles(AllianceRole.MEMBER)
  updateNews(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('newsId') newsId: string,
    @Body() dto: UpdateTeamNewsDto,
  ) {
    return this.teamNews.update(teamId, newsId, req.user.userId, dto);
  }

  @Delete(':teamId/news/:newsId')
  @Roles(AllianceRole.MEMBER)
  async deleteNews(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('newsId') newsId: string,
  ) {
    await this.teamNews.delete(teamId, newsId, req.user.userId);
    return { ok: true };
  }

  @Post(':teamId/news/:newsId/vote')
  @Roles(AllianceRole.MEMBER)
  voteNews(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('newsId') newsId: string,
    @Body() dto: VoteTeamNewsDto,
  ) {
    return this.teamNews.vote(
      teamId,
      newsId,
      req.user.userId,
      dto.optionId.trim(),
    );
  }

  @Get(':teamId/forum/topics')
  @Roles(AllianceRole.MEMBER)
  listForumTopics(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
  ) {
    return this.teamForum.listTopics(teamId, req.user.userId);
  }

  @Post(':teamId/forum/topics')
  @Roles(AllianceRole.MEMBER)
  createForumTopic(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Body() dto: CreateTeamForumTopicDto,
  ) {
    return this.teamForum.createTopic(teamId, req.user.userId, dto.title);
  }

  @Patch(':teamId/forum/topics/:topicId')
  @Roles(AllianceRole.MEMBER)
  patchForumTopic(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Body() dto: UpdateTeamForumTopicDto,
  ) {
    return this.teamForum.updateTopic(
      teamId,
      topicId,
      req.user.userId,
      dto.title,
    );
  }

  @Delete(':teamId/forum/topics/:topicId')
  @Roles(AllianceRole.MEMBER)
  async deleteForumTopic(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
  ) {
    await this.teamForum.deleteTopic(teamId, topicId, req.user.userId);
    return { ok: true };
  }

  @Get(':teamId/forum/topics/:topicId/messages')
  @Roles(AllianceRole.MEMBER)
  listForumMessages(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Query('before') before?: string,
    @Query('limit') limitRaw?: string,
  ) {
    const lim = limitRaw != null ? Number.parseInt(limitRaw, 10) : 50;
    return this.teamForum.listMessages(
      teamId,
      topicId,
      req.user.userId,
      before,
      Number.isFinite(lim) ? lim : 50,
    );
  }

  @Post(':teamId/forum/topics/:topicId/read')
  @Roles(AllianceRole.MEMBER)
  markForumTopicRead(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Body() dto: MarkTeamForumTopicReadDto,
  ) {
    return this.teamForum.markTopicRead(
      teamId,
      topicId,
      req.user.userId,
      dto.messageId?.trim() ?? '',
    );
  }

  @Post(':teamId/forum/topics/:topicId/messages')
  @Roles(AllianceRole.MEMBER)
  async postForumMessage(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Body() dto: CreateTeamForumMessageDto,
  ) {
    const message = await this.teamForum.postMessage(
      teamId,
      topicId,
      req.user.userId,
      dto.text ?? '',
      dto.replyToMessageId?.trim() || null,
      (dto.imageFileIds ?? []).map((x) => (typeof x === 'string' ? x.trim() : '')).filter(Boolean),
      dto.imageFileId?.trim() || null,
      dto.fileFileId?.trim() || null,
    );
    this.teamForumGateway.broadcastNewMessage(teamId, topicId, message);
    return message;
  }

  @Post(':teamId/forum/topics/:topicId/messages/:messageId/forward')
  @Roles(AllianceRole.MEMBER)
  async forwardForumMessage(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Param('messageId') messageId: string,
  ) {
    const message = await this.teamForum.forwardMessage(
      teamId,
      topicId,
      req.user.userId,
      messageId,
    );
    this.teamForumGateway.broadcastNewMessage(teamId, topicId, message);
    return message;
  }

  @Patch(':teamId/forum/topics/:topicId/messages/:messageId')
  @Roles(AllianceRole.MEMBER)
  async patchForumMessage(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Param('messageId') messageId: string,
    @Body() dto: UpdateTeamForumMessageDto,
  ) {
    const message = await this.teamForum.patchMessage(
      teamId,
      topicId,
      messageId,
      req.user.userId,
      dto.text,
    );
    this.teamForumGateway.broadcastMessageEdited(teamId, topicId, message);
    return message;
  }

  @Delete(':teamId/forum/topics/:topicId/messages/:messageId')
  @Roles(AllianceRole.MEMBER)
  async deleteForumMessage(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Param('messageId') messageId: string,
  ) {
    await this.teamForum.deleteMessage(
      teamId,
      topicId,
      messageId,
      req.user.userId,
    );
    const deletedAt = new Date().toISOString();
    this.teamForumGateway.broadcastMessageDeleted(
      teamId,
      topicId,
      messageId,
      deletedAt,
      req.user.userId,
    );
    return { ok: true };
  }

  @Post(':teamId/forum/topics/:topicId/messages/bulk-delete')
  @Roles(AllianceRole.MEMBER)
  async bulkDeleteForumMessages(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Body() dto: BulkDeleteTeamForumMessagesDto,
  ) {
    const ids = Array.isArray(dto.messageIds)
      ? dto.messageIds.map((x) => (typeof x === 'string' ? x.trim() : '')).filter(Boolean)
      : [];
    const deletedAt = new Date().toISOString();
    const deletedIds = await this.teamForum.bulkDeleteMessages(
      teamId,
      topicId,
      ids,
      req.user.userId,
    );
    for (const id of deletedIds) {
      this.teamForumGateway.broadcastMessageDeleted(
        teamId,
        topicId,
        id,
        deletedAt,
        req.user.userId,
      );
    }
    return { ok: true };
  }

  @Get(':teamId')
  @Roles(AllianceRole.MEMBER)
  getTeam(@Req() req: { user: RequestUser }, @Param('teamId') teamId: string) {
    return this.teams.getTeamDetailForUser(teamId, req.user.userId);
  }

  @Patch(':teamId/display')
  @Roles(AllianceRole.MEMBER)
  updateTeamBranding(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Body() dto: UpdatePlayerTeamDisplayNameDto,
  ) {
    return this.teams.updateTeamBranding(
      teamId,
      req.user.userId,
      dto.displayName,
      dto.tag,
    );
  }

  @Patch(':teamId/members/:memberUserId/role')
  @Roles(AllianceRole.MEMBER)
  updateMemberSquadRole(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('memberUserId') memberUserId: string,
    @Body() dto: UpdateSquadMemberRoleDto,
  ) {
    return this.teams.updateMemberSquadRole(
      teamId,
      req.user.userId,
      memberUserId,
      dto.role,
    );
  }

  @Post(':teamId/join-requests')
  @Roles(AllianceRole.MEMBER)
  submitJoin(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
  ) {
    return this.teams.submitJoinRequest(teamId, req.user.userId);
  }

  @Post(':teamId/members')
  @Roles(AllianceRole.MEMBER)
  addMember(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Body() dto: AddTeamMemberDto,
  ) {
    return this.teams.addMemberByUsername(
      teamId,
      req.user.userId,
      dto.username,
    );
  }

  @Delete(':teamId/members/:userId')
  @Roles(AllianceRole.MEMBER)
  removeMember(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('userId') userId: string,
  ) {
    return this.teams.removeMember(teamId, req.user.userId, userId);
  }
}
