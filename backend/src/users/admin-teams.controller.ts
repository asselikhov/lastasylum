import {
  Body,
  Controller,
  Delete,
  Get,
  NotFoundException,
  Param,
  Patch,
  Query,
  UseGuards,
} from '@nestjs/common';
import { UpdatePlayerTeamAdminDto } from './dto/update-player-team-admin.dto';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { ChatRoomsService } from '../chat/chat-rooms.service';
import { ChatGateway } from '../chat/chat.gateway';
import { ChatService } from '../chat/chat.service';
import { TeamsService } from './teams.service';
import { TeamNewsService } from './team-news.service';
import { TeamForumService } from './team-forum.service';
import { UsersService } from './users.service';
import { PinAuditService } from './pin-audit.service';

@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
export class AdminTeamsController {
  constructor(
    private readonly teams: TeamsService,
    private readonly users: UsersService,
    private readonly chatRooms: ChatRoomsService,
    private readonly chat: ChatService,
    private readonly chatGateway: ChatGateway,
    private readonly teamNews: TeamNewsService,
    private readonly teamForum: TeamForumService,
    private readonly pinAudit: PinAuditService,
  ) {}

  @Get('player-teams')
  @Roles(AllianceRole.ADMIN)
  listPlayerTeams(
    @Query('serverNumber') serverNumberRaw?: string,
    @Query('skip') skipRaw?: string,
    @Query('limit') limitRaw?: string,
  ) {
    const serverNumber =
      serverNumberRaw != null && serverNumberRaw.trim() !== ''
        ? Number.parseInt(serverNumberRaw, 10)
        : undefined;
    const skip = Math.max(0, Number.parseInt(skipRaw ?? '0', 10) || 0);
    const limit = Math.min(
      200,
      Math.max(1, Number.parseInt(limitRaw ?? '50', 10) || 50),
    );
    return this.teams.listAllTeamsForAdmin({
      serverNumber: Number.isFinite(serverNumber) ? serverNumber : undefined,
      skip,
      limit,
    });
  }

  @Get('player-teams/:teamId')
  @Roles(AllianceRole.ADMIN)
  async getPlayerTeam(@Param('teamId') teamId: string) {
    const detail = await this.teams.getTeamDetailForAdmin(teamId);
    if (!detail) {
      throw new NotFoundException('Team not found');
    }
    return detail;
  }

  @Patch('player-teams/:teamId')
  @Roles(AllianceRole.ADMIN)
  async updatePlayerTeam(
    @Param('teamId') teamId: string,
    @Body() dto: UpdatePlayerTeamAdminDto,
  ) {
    if (dto.displayName == null && dto.tag == null) {
      return { ok: true };
    }
    return this.teams.updateTeamBrandingForAdmin(
      teamId,
      dto.displayName,
      dto.tag,
    );
  }

  @Get('player-teams/:teamId/chat-rooms')
  @Roles(AllianceRole.ADMIN)
  listTeamChatRooms(@Param('teamId') teamId: string) {
    return this.chatRooms.listForPlayerTeamAdmin(teamId);
  }

  @Get('player-teams/:teamId/news')
  @Roles(AllianceRole.ADMIN)
  listTeamNews(
    @Param('teamId') teamId: string,
    @Query('limit') limitRaw?: string,
  ) {
    const limit = limitRaw != null ? Number.parseInt(limitRaw, 10) : 100;
    return this.teamNews.listForAdmin(
      teamId,
      Number.isFinite(limit) ? limit : 100,
    );
  }

  @Get('player-teams/:teamId/forum/topics')
  @Roles(AllianceRole.ADMIN)
  listTeamForumTopics(@Param('teamId') teamId: string) {
    return this.teamForum.listTopicsForAdmin(teamId);
  }

  @Get('player-teams/:teamId/forum/topics/:topicId/messages')
  @Roles(AllianceRole.ADMIN)
  listTeamForumMessages(
    @Param('teamId') teamId: string,
    @Param('topicId') topicId: string,
    @Query('limit') limitRaw?: string,
  ) {
    const limit = limitRaw != null ? Number.parseInt(limitRaw, 10) : 100;
    return this.teamForum.listMessagesForAdmin(
      teamId,
      topicId,
      Number.isFinite(limit) ? limit : 100,
    );
  }

  @Delete('chat/messages')
  @Roles(AllianceRole.ADMIN)
  async clearAllChatMessages() {
    const result = await this.chat.clearAllChatHistoryForAdmin();
    this.chatGateway.broadcastChatHistoryCleared();
    return result;
  }

  @Get('chat-rooms/:roomId/messages')
  @Roles(AllianceRole.ADMIN)
  listChatRoomMessages(
    @Param('roomId') roomId: string,
    @Query('before') before?: string,
    @Query('limit') limitRaw?: string,
  ) {
    const limit = limitRaw != null ? Number.parseInt(limitRaw, 10) : 80;
    return this.chat.getRecentMessagesForAdmin(roomId, {
      before,
      limit: Number.isFinite(limit) ? limit : 80,
    });
  }

  @Get('users/without-team')
  @Roles(AllianceRole.ADMIN)
  async listUsersWithoutTeam(
    @Query('q') q?: string,
    @Query('skip') skipRaw?: string,
    @Query('limit') limitRaw?: string,
  ) {
    const skip = Math.max(0, Number.parseInt(skipRaw ?? '0', 10) || 0);
    const limit = Math.min(
      500,
      Math.max(1, Number.parseInt(limitRaw ?? '200', 10) || 200),
    );
    const docs = await this.users.listUsersWithoutTeamForAdmin({
      q: q?.trim() || undefined,
      skip,
      limit,
    });
    return Promise.all(docs.map((u) => this.users.toSafeUser(u)));
  }

  @Get('player-teams/:teamId/pin-audit')
  @Roles(AllianceRole.ADMIN)
  listPinAudit(
    @Param('teamId') teamId: string,
    @Query('limit') limitRaw?: string,
  ) {
    const limit = Math.min(
      200,
      Math.max(1, Number.parseInt(limitRaw ?? '50', 10) || 50),
    );
    return this.pinAudit.listForTeam(teamId, limit);
  }

  @Get('overview')
  @Roles(AllianceRole.ADMIN)
  async overview() {
    const [teams, withoutTeamCount] = await Promise.all([
      this.teams.countTeams(),
      this.users.countUsersWithoutTeam(),
    ]);
    return { playerTeamCount: teams, usersWithoutTeamCount: withoutTeamCount };
  }
}
