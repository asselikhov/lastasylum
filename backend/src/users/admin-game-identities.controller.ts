import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Query,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { UpdateGameIdentityDto } from './dto/game-identity.dto';
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
  @Roles(AllianceRole.R5)
  listGameServers() {
    return this.gameIdentities.listServerSummariesForAdmin();
  }

  @Get('game-identities/users')
  @Roles(AllianceRole.R5)
  async listUsersOnServers(
    @Query('serverNumber') serverNumberRaw?: string,
    @Query('q') q?: string,
  ) {
    const serverNumber =
      serverNumberRaw != null && serverNumberRaw.trim() !== ''
        ? Number.parseInt(serverNumberRaw, 10)
        : undefined;
    return this.gameIdentities.listUsersForAdminByServer({
      serverNumber: Number.isFinite(serverNumber) ? serverNumber : undefined,
      q: q?.trim() || undefined,
    });
  }

  @Patch('users/:userId/game-identities/:identityId')
  @Roles(AllianceRole.R5)
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
