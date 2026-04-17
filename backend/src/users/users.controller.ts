import {
  Body,
  Controller,
  Get,
  NotFoundException,
  Patch,
  Req,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { UpdateRoleDto } from './dto/update-role.dto';
import { MuteUserDto } from './dto/mute-user.dto';
import { UsersService } from './users.service';

type RequestUser = {
  userId: string;
};

@Controller('users')
@UseGuards(JwtAuthGuard, RolesGuard)
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get('me')
  @Roles(AllianceRole.R2)
  async getMyProfile(@Req() req: { user: RequestUser }) {
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new NotFoundException('User not found');
    }

    return this.usersService.toSafeUser(user);
  }

  @Get()
  @Roles(AllianceRole.R4)
  async listAllianceMembers() {
    const users = await this.usersService.listByAlliance('OBZHORY');
    return users.map((user) => this.usersService.toSafeUser(user));
  }

  @Roles(AllianceRole.R5)
  @Patch('role')
  async updateRole(@Body() dto: UpdateRoleDto) {
    const updatedUser = await this.usersService.updateRole(
      dto.userId,
      dto.role,
    );
    if (!updatedUser) {
      throw new NotFoundException('User not found');
    }

    return this.usersService.toSafeUser(updatedUser);
  }

  @Roles(AllianceRole.R4)
  @Patch('mute')
  async muteUser(@Body() dto: MuteUserDto) {
    const mutedUntil = new Date(Date.now() + dto.minutes * 60 * 1000);
    const updatedUser = await this.usersService.muteUser(
      dto.userId,
      mutedUntil,
    );
    if (!updatedUser) {
      throw new NotFoundException('User not found');
    }

    return this.usersService.toSafeUser(updatedUser);
  }
}
