import {
  Controller,
  Get,
  NotFoundException,
  Param,
  Query,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { TeamsService } from './teams.service';
import { UsersService } from './users.service';

@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
export class AdminTeamsController {
  constructor(
    private readonly teams: TeamsService,
    private readonly users: UsersService,
  ) {}

  @Get('player-teams')
  @Roles(AllianceRole.R5)
  listPlayerTeams() {
    return this.teams.listAllTeamsForAdmin();
  }

  @Get('player-teams/:teamId')
  @Roles(AllianceRole.R5)
  async getPlayerTeam(@Param('teamId') teamId: string) {
    const detail = await this.teams.getTeamDetailForAdmin(teamId);
    if (!detail) {
      throw new NotFoundException('Team not found');
    }
    return detail;
  }

  @Get('users/without-team')
  @Roles(AllianceRole.R5)
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

  @Get('overview')
  @Roles(AllianceRole.R5)
  async overview() {
    const [teams, withoutTeamCount] = await Promise.all([
      this.teams.countTeams(),
      this.users.countUsersWithoutTeam(),
    ]);
    return { playerTeamCount: teams, usersWithoutTeamCount: withoutTeamCount };
  }
}
