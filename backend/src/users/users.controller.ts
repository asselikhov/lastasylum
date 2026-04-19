import {
  Body,
  Controller,
  Delete,
  Get,
  NotFoundException,
  Param,
  Patch,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DEFAULT_ALLIANCE_ID } from '../common/constants/default-alliance-id';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { UpdateMembershipDto } from './dto/update-membership.dto';
import { UpdateRoleDto } from './dto/update-role.dto';
import { UpdateUsernameDto } from './dto/update-username.dto';
import { UpdateTelegramDto } from './dto/update-telegram.dto';
import { UpdateTeamDisplayNameDto } from './dto/update-team-display.dto';
import { MuteUserDto } from './dto/mute-user.dto';
import {
  RegisterPushTokenDto,
  UpdatePresenceDto,
} from './dto/register-push-token.dto';
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

  @Post('me/push-token')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  async registerPushToken(
    @Req() req: { user: RequestUser },
    @Body() dto: RegisterPushTokenDto,
  ) {
    await this.usersService.registerPushToken(req.user.userId, dto.token);
    return { success: true };
  }

  @Delete('me/push-tokens')
  @Roles(AllianceRole.R2)
  async clearPushTokens(@Req() req: { user: RequestUser }) {
    await this.usersService.clearPushTokens(req.user.userId);
    return { success: true };
  }

  @Post('me/presence')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 120, ttl: 60_000 } })
  async updatePresence(
    @Req() req: { user: RequestUser },
    @Body() dto: UpdatePresenceDto,
  ) {
    await this.usersService.updatePresence(req.user.userId, dto.status);
    return { success: true };
  }

  @Patch('me/telegram')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  async updateMyTelegram(
    @Req() req: { user: RequestUser },
    @Body() dto: UpdateTelegramDto,
  ) {
    const updated = await this.usersService.updateMyTelegramUsername(
      req.user.userId,
      dto.username,
    );
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return this.usersService.toSafeUser(updated);
  }

  @Patch('me/username')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 10, ttl: 60_000 } })
  async updateMyUsername(
    @Req() req: { user: RequestUser },
    @Body() dto: UpdateUsernameDto,
  ) {
    const updated = await this.usersService.updateUsername(
      req.user.userId,
      dto.username,
    );
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return this.usersService.toSafeUser(updated);
  }

  @Patch('me/team')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  async updateMyTeamDisplayName(
    @Req() req: { user: RequestUser },
    @Body() dto: UpdateTeamDisplayNameDto,
  ) {
    const updated = await this.usersService.updateMyTeamDisplayName(
      req.user.userId,
      dto.name,
    );
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return this.usersService.toSafeUser(updated);
  }

  @Get()
  @Roles(AllianceRole.R4)
  async listAllianceMembers() {
    const users = await this.usersService.listByAlliance(DEFAULT_ALLIANCE_ID);
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

  /** Must be before :id routes so "mute" is not captured as id. */
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

  @Roles(AllianceRole.R5)
  @Patch(':id/membership')
  async updateMembership(
    @Param('id') id: string,
    @Body() dto: UpdateMembershipDto,
  ) {
    const updatedUser = await this.usersService.updateMembershipStatus(
      id,
      dto.status,
    );
    if (!updatedUser) {
      throw new NotFoundException('User not found');
    }
    return this.usersService.toSafeUser(updatedUser);
  }

  @Roles(AllianceRole.R5)
  @Patch(':id/username')
  async updateUsername(@Param('id') id: string, @Body() dto: UpdateUsernameDto) {
    const updatedUser = await this.usersService.updateUsername(
      id,
      dto.username,
    );
    if (!updatedUser) {
      throw new NotFoundException('User not found');
    }
    return this.usersService.toSafeUser(updatedUser);
  }

  @Roles(AllianceRole.R5)
  @Delete(':id')
  async deleteUser(@Param('id') id: string, @Req() req: { user: RequestUser }) {
    if (id === req.user.userId) {
      throw new NotFoundException('Cannot delete your own account here');
    }
    const ok = await this.usersService.deleteUser(id);
    if (!ok) {
      throw new NotFoundException('User not found');
    }
    return { success: true };
  }
}
