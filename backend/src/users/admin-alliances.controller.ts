import { Body, Controller, Get, Param, Patch, UseGuards } from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { AllianceRegistryService } from './alliance-registry.service';
import { UpdateAllianceOverlayDto } from './dto/update-alliance-overlay.dto';

@Controller('admin/alliances')
@UseGuards(JwtAuthGuard, RolesGuard)
export class AdminAlliancesController {
  constructor(private readonly registry: AllianceRegistryService) {}

  @Get()
  @Roles(AllianceRole.R5)
  list() {
    return this.registry.listForAdmin();
  }

  @Patch(':publicId/overlay')
  @Roles(AllianceRole.R5)
  updateOverlay(
    @Param('publicId') publicId: string,
    @Body() dto: UpdateAllianceOverlayDto,
  ) {
    return this.registry.updateOverlayByPublicId(publicId, dto.overlayEnabled);
  }
}
