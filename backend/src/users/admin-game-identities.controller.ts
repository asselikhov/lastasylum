import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { CreateGameIdentityDto, UpdateGameIdentityDto } from './dto/game-identity.dto';
import { GameIdentitiesService } from './game-identities.service';
import { UsersService } from './users.service';

@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
export class AdminGameIdentitiesController {
  constructor(
    private readonly gameIdentities: GameIdentitiesService,
    private readonly users: UsersService,
  ) {}

  @Get('game-servers')
  @Roles(AllianceRole.ADMIN)
  listGameServers() {
    return this.gameIdentities.listServerSummariesForAdmin();
  }

  @Get('game-identities/users')
  @Roles(AllianceRole.ADMIN)
  async listUsersOnServers(
    @Query('serverNumber') serverNumberRaw?: string,
    @Query('q') q?: string,
    @Query('withoutTeam') withoutTeamRaw?: string,
    @Query('skip') skipRaw?: string,
    @Query('limit') limitRaw?: string,
  ) {
    const serverNumber =
      serverNumberRaw != null && serverNumberRaw.trim() !== ''
        ? Number.parseInt(serverNumberRaw, 10)
        : undefined;
    const withoutTeam =
      withoutTeamRaw === '1' ||
      withoutTeamRaw === 'true' ||
      withoutTeamRaw === 'yes';
    const skip = Math.max(0, Number.parseInt(skipRaw ?? '0', 10) || 0);
    const limit = Math.min(
      200,
      Math.max(1, Number.parseInt(limitRaw ?? '50', 10) || 50),
    );
    return this.gameIdentities.listUsersForAdminByServer({
      serverNumber: Number.isFinite(serverNumber) ? serverNumber : undefined,
      q: q?.trim() || undefined,
      withoutTeam,
      skip,
      limit,
    });
  }

  @Post('users/:userId/game-identities')
  @Roles(AllianceRole.ADMIN)
  async adminCreateGameIdentity(
    @Param('userId') userId: string,
    @Body() dto: CreateGameIdentityDto,
  ) {
    const updated = await this.gameIdentities.adminBootstrapGameIdentity(
      userId,
      {
        serverNumber: dto.serverNumber,
        gameNickname: dto.gameNickname,
      },
    );
    return this.users.toSafeUser(updated);
  }

  @Patch('users/:userId/game-identities/:identityId')
  @Roles(AllianceRole.ADMIN)
  async adminUpdateGameIdentity(
    @Param('userId') userId: string,
    @Param('identityId') identityId: string,
    @Body() dto: UpdateGameIdentityDto,
  ) {
    const updated = await this.gameIdentities.updateIdentity(
      userId,
      identityId,
      dto,
    );
    return this.users.toSafeUser(updated);
  }
}
