import {
  BadRequestException,
  Body,
  Controller,
  Delete,
  Get,
  Header,
  Logger,
  NotFoundException,
  Param,
  Patch,
  Post,
  Query,
  Req,
  Res,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { FileInterceptor } from '@nestjs/platform-express';
import { memoryStorage } from 'multer';
import type { Response } from 'express';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DEFAULT_ALLIANCE_ID } from '../common/constants/default-alliance-id';
import {
  assertUploadSizeWithinLimit,
  FREE_TIER_MAX_UPLOAD_BYTES,
  withUploadSlot,
} from '../common/upload-concurrency';
import { Roles } from '../common/decorators/roles.decorator';
import { isAppAdminRole } from '../common/alliance-role.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { UpdateMembershipDto } from './dto/update-membership.dto';
import { UpdateRoleDto } from './dto/update-role.dto';
import { UpdateUsernameDto } from './dto/update-username.dto';
import { UpdateTelegramDto } from './dto/update-telegram.dto';
import { UpdateNotificationPreferencesDto } from './dto/update-notification-preferences.dto';
import { MuteUserDto } from './dto/mute-user.dto';
import {
  RegisterPushTokenDto,
  UpdatePresenceDto,
} from './dto/register-push-token.dto';
import {
  CreateGameIdentityDto,
  SwitchActiveGameIdentityDto,
  UpdateGameIdentityDto,
} from './dto/game-identity.dto';
import { GameIdentitiesService } from './game-identities.service';
import { UserDocument } from './schemas/user.schema';
import { TeamPresenceGateway } from './team-presence.gateway';
import { UsersService } from './users.service';
import { UserAvatarService } from './user-avatar.service';

type RequestUser = {
  userId: string;
};

@Controller('users')
@UseGuards(JwtAuthGuard, RolesGuard)
export class UsersController {
  private readonly logger = new Logger(UsersController.name);

  constructor(
    private readonly usersService: UsersService,
    private readonly gameIdentities: GameIdentitiesService,
    private readonly teamPresenceGateway: TeamPresenceGateway,
    private readonly userAvatarService: UserAvatarService,
  ) {}

  @Get('me')
  @Roles(AllianceRole.MEMBER)
  async getMyProfile(@Req() req: { user: RequestUser }) {
    const user = await this.usersService.findByIdRaw(req.user.userId);
    if (!user) {
      throw new NotFoundException('User not found');
    }

    return await this.usersService.toSafeUser(user);
  }

  @Post('me/avatar')
  @Roles(AllianceRole.MEMBER)
  @UseInterceptors(
    FileInterceptor('file', {
      storage: memoryStorage(),
      limits: { fileSize: 2 * 1024 * 1024 },
    }),
  )
  async uploadMyAvatar(
    @Req() req: { user: RequestUser },
    @UploadedFile() file: Express.Multer.File | undefined,
  ) {
    if (!file?.buffer?.length) {
      throw new BadRequestException('file is required');
    }
    return withUploadSlot(async () => {
      assertUploadSizeWithinLimit(file.size, FREE_TIER_MAX_UPLOAD_BYTES);
      const result = await this.userAvatarService.upload({
        userId: req.user.userId,
        buffer: file.buffer,
        mimeType: file.mimetype,
        size: file.size,
      });
      this.usersService.invalidateSafeUserCache(req.user.userId);
      return result;
    });
  }

  @Delete('me/avatar')
  @Roles(AllianceRole.MEMBER)
  async deleteMyAvatar(@Req() req: { user: RequestUser }) {
    await this.userAvatarService.delete(req.user.userId);
    this.usersService.invalidateSafeUserCache(req.user.userId);
    return { success: true };
  }

  @Get('avatars/:userId')
  @Roles(AllianceRole.MEMBER)
  @Header('Cache-Control', 'private, max-age=3600')
  async getUserAvatar(
    @Req() req: { user: RequestUser },
    @Param('userId') userId: string,
    @Res() res: Response,
  ) {
    await this.userAvatarService.assertMayViewAvatar(req.user.userId, userId);
    const dl = await this.userAvatarService.openDownload(userId);
    res.setHeader('Content-Type', dl.mimeType);
    dl.stream.on('error', () => res.status(404).end());
    dl.stream.pipe(res);
  }

  @Post('me/push-token')
  @Roles(AllianceRole.MEMBER)
  async registerPushToken(
    @Req() req: { user: RequestUser },
    @Body() dto: RegisterPushTokenDto,
  ) {
    await this.usersService.registerPushToken(req.user.userId, dto.token);
    this.logger.log(
      `push-token registered userId=${req.user.userId} len=${dto.token.length}`,
    );
    return { success: true };
  }

  @Delete('me/push-tokens')
  @Roles(AllianceRole.MEMBER)
  async clearPushTokens(@Req() req: { user: RequestUser }) {
    await this.usersService.clearPushTokens(req.user.userId);
    return { success: true };
  }

  @Post('me/presence')
  @Roles(AllianceRole.MEMBER)
  async updatePresence(
    @Req() req: { user: RequestUser },
    @Body() dto: UpdatePresenceDto,
  ) {
    await this.usersService.updatePresence(req.user.userId, dto.status);
    void this.teamPresenceGateway.broadcastUserPresence(req.user.userId);
    return { success: true };
  }

  @Patch('me/notification-preferences')
  @Roles(AllianceRole.MEMBER)
  async updateMyNotificationPreferences(
    @Req() req: { user: RequestUser },
    @Body() dto: UpdateNotificationPreferencesDto,
  ) {
    const updated = await this.usersService.updateNotificationPreferences(
      req.user.userId,
      dto,
    );
    if (!updated) {
      throw new NotFoundException('User not found');
    }
    return await this.usersService.toSafeUser(updated);
  }

  @Patch('me/telegram')
  @Roles(AllianceRole.MEMBER)
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
    return await this.usersService.toSafeUser(updated);
  }

  @Patch('me/username')
  @Roles(AllianceRole.MEMBER)
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
    return await this.usersService.toSafeUser(updated);
  }

  @Post('me/game-identities')
  @Roles(AllianceRole.MEMBER)
  async addGameIdentity(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateGameIdentityDto,
  ) {
    const updated = await this.gameIdentities.addIdentity(
      req.user.userId,
      dto.serverNumber,
      dto.gameNickname,
    );
    return await this.usersService.toSafeUser(updated);
  }

  @Patch('me/game-identities/:identityId')
  @Roles(AllianceRole.MEMBER)
  async updateGameIdentity(
    @Req() req: { user: RequestUser },
    @Param('identityId') identityId: string,
    @Body() dto: UpdateGameIdentityDto,
  ) {
    const updated = await this.gameIdentities.updateIdentity(
      req.user.userId,
      identityId,
      dto,
    );
    return await this.usersService.toSafeUser(updated);
  }

  @Delete('me/game-identities/:identityId')
  @Roles(AllianceRole.MEMBER)
  async deleteGameIdentity(
    @Req() req: { user: RequestUser },
    @Param('identityId') identityId: string,
  ) {
    const updated = await this.gameIdentities.removeIdentity(
      req.user.userId,
      identityId,
    );
    return await this.usersService.toSafeUser(updated);
  }

  @Post('me/active-game-identity')
  @Roles(AllianceRole.MEMBER)
  async switchActiveGameIdentity(
    @Req() req: { user: RequestUser },
    @Body() dto: SwitchActiveGameIdentityDto,
  ) {
    const updated = await this.gameIdentities.switchActive(
      req.user.userId,
      dto.gameIdentityId,
    );
    return await this.usersService.toSafeUser(updated);
  }

  @Get()
  @Roles(AllianceRole.MODERATOR)
  async listAllianceMembers(
    @Req() req: { user: RequestUser },
    @Query('allianceCode') allianceCode?: string,
    @Query('withoutTeam') withoutTeam?: string,
    @Query('q') q?: string,
    @Query('skip') skipRaw?: string,
    @Query('limit') limitRaw?: string,
  ) {
    const me = await this.usersService.findById(req.user.userId);
    if (!me) {
      throw new NotFoundException('User not found');
    }

    let usersList: UserDocument[];
    if (isAppAdminRole(me.role)) {
      const skip = Math.max(0, Number.parseInt(skipRaw ?? '0', 10) || 0);
      const limit = Math.min(
        500,
        Math.max(1, Number.parseInt(limitRaw ?? '200', 10) || 200),
      );
      usersList = await this.usersService.listUsersForAdmin({
        allianceCode: allianceCode?.trim() || undefined,
        withoutTeam: withoutTeam === 'true' || withoutTeam === '1',
        q: q?.trim() || undefined,
        skip,
        limit,
      });
    } else {
      const allianceName = me.allianceName?.trim() || DEFAULT_ALLIANCE_ID;
      usersList = await this.usersService.listByAlliance(allianceName);
    }

    return Promise.all(
      usersList.map((user) => this.usersService.toSafeUser(user)),
    );
  }

  @Roles(AllianceRole.ADMIN)
  @Patch('role')
  async updateRole(@Body() dto: UpdateRoleDto) {
    const updatedUser = await this.usersService.updateRole(
      dto.userId,
      dto.role,
    );
    if (!updatedUser) {
      throw new NotFoundException('User not found');
    }

    return await this.usersService.toSafeUser(updatedUser);
  }

  /** Must be before :id routes so "mute" is not captured as id. */
  @Roles(AllianceRole.MODERATOR)
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

    return await this.usersService.toSafeUser(updatedUser);
  }

  @Roles(AllianceRole.ADMIN)
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
    return await this.usersService.toSafeUser(updatedUser);
  }

  @Roles(AllianceRole.ADMIN)
  @Patch(':id/username')
  async updateUsername(
    @Param('id') id: string,
    @Body() dto: UpdateUsernameDto,
  ) {
    const updatedUser = await this.usersService.updateUsername(
      id,
      dto.username,
    );
    if (!updatedUser) {
      throw new NotFoundException('User not found');
    }
    return await this.usersService.toSafeUser(updatedUser);
  }

  @Roles(AllianceRole.ADMIN)
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
