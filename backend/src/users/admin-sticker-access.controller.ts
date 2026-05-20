import {
  BadRequestException,
  Body,
  Controller,
  Get,
  Param,
  Put,
  UseGuards,
} from '@nestjs/common';
import { Types } from 'mongoose';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { PutAllianceStickerAccessDto } from './dto/sticker-access.dto';
import { StickerAccessService } from './sticker-access.service';
import { UsersService } from './users.service';

@Controller('admin/sticker-access')
@UseGuards(JwtAuthGuard, RolesGuard)
export class AdminStickerAccessController {
  constructor(
    private readonly stickerAccess: StickerAccessService,
    private readonly usersService: UsersService,
  ) {}

  @Get(':allianceCode')
  @Roles(AllianceRole.ADMIN)
  getOne(@Param('allianceCode') allianceCode: string) {
    return this.stickerAccess.getAllianceAccess(allianceCode);
  }

  @Put(':allianceCode')
  @Roles(AllianceRole.ADMIN)
  async putOne(
    @Param('allianceCode') allianceCode: string,
    @Body() dto: PutAllianceStickerAccessDto,
  ) {
    const alliance = allianceCode.trim();
    const userIds = [
      ...new Set(
        Object.values(dto.userGrants ?? {}).flatMap((ids) =>
          (ids ?? []).map((id) => id.trim()),
        ),
      ),
    ].filter((id) => Types.ObjectId.isValid(id));

    for (const id of userIds) {
      const u = await this.usersService.findById(id);
      if (!u || u.allianceName.trim() !== alliance) {
        throw new BadRequestException(
          `User ${id} is not in alliance ${alliance}`,
        );
      }
    }

    return this.stickerAccess.replaceAllianceAccess(allianceCode, dto);
  }
}
