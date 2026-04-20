import {
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
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { AddTeamMemberDto } from './dto/add-team-member.dto';
import { CreatePlayerTeamDto } from './dto/create-player-team.dto';
import { UpdatePlayerTeamDisplayNameDto } from './dto/update-player-team-display.dto';
import { UpdateSquadMemberRoleDto } from './dto/update-squad-member-role.dto';
import { TeamsService } from './teams.service';

type RequestUser = {
  userId: string;
};

@Controller('teams')
@UseGuards(JwtAuthGuard, RolesGuard)
export class TeamsController {
  constructor(private readonly teams: TeamsService) {}

  @Post()
  @Roles(AllianceRole.R2)
  createTeam(
    @Req() req: { user: RequestUser },
    @Body() dto: CreatePlayerTeamDto,
  ) {
    return this.teams.createTeam(req.user.userId, dto.displayName, dto.tag);
  }

  @Get('search')
  @Roles(AllianceRole.R2)
  search(@Query('q') q: string, @Query('limit') limit?: string) {
    const lim = limit != null ? Number.parseInt(limit, 10) : 20;
    return this.teams.searchTeams(q ?? '', Number.isFinite(lim) ? lim : 20);
  }

  @Get('me/join-requests')
  @Roles(AllianceRole.R2)
  myJoinRequests(@Req() req: { user: RequestUser }) {
    return this.teams.listPendingJoinRequestsForLeader(req.user.userId);
  }

  @Post('join-requests/:requestId/accept')
  @Roles(AllianceRole.R2)
  acceptRequest(
    @Req() req: { user: RequestUser },
    @Param('requestId') requestId: string,
  ) {
    return this.teams.acceptJoinRequest(requestId, req.user.userId);
  }

  @Post('join-requests/:requestId/reject')
  @Roles(AllianceRole.R2)
  rejectRequest(
    @Req() req: { user: RequestUser },
    @Param('requestId') requestId: string,
  ) {
    return this.teams.rejectJoinRequest(requestId, req.user.userId);
  }

  @Get(':teamId')
  @Roles(AllianceRole.R2)
  getTeam(@Req() req: { user: RequestUser }, @Param('teamId') teamId: string) {
    return this.teams.getTeamDetailForUser(teamId, req.user.userId);
  }

  @Patch(':teamId/display')
  @Roles(AllianceRole.R2)
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
  @Roles(AllianceRole.R2)
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
  @Roles(AllianceRole.R2)
  submitJoin(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
  ) {
    return this.teams.submitJoinRequest(teamId, req.user.userId);
  }

  @Post(':teamId/members')
  @Roles(AllianceRole.R2)
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
  @Roles(AllianceRole.R2)
  removeMember(
    @Req() req: { user: RequestUser },
    @Param('teamId') teamId: string,
    @Param('userId') userId: string,
  ) {
    return this.teams.removeMember(teamId, req.user.userId, userId);
  }
}
