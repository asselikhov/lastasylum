import {
  Body,
  Controller,
  NotFoundException,
  Param,
  Patch,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { UpdateOverlayGameSearchDto } from './dto/update-overlay-game-search.dto';
import { UsersService } from './users.service';

@Controller('admin')
@UseGuards(JwtAuthGuard, RolesGuard)
export class AdminUserFeaturesController {
  constructor(private readonly users: UsersService) {}

  @Patch('users/:userId/overlay-game-search')
  @Roles(AllianceRole.ADMIN)
  async updateOverlayGameSearch(
    @Param('userId') userId: string,
    @Body() dto: UpdateOverlayGameSearchDto,
  ) {
    const updated = await this.users.updateOverlayGameSearchEnabled(
      userId,
      dto.overlayGameSearchEnabled,
    );
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return this.users.toSafeUser(updated);
  }
}
