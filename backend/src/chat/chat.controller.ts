import {
  BadRequestException,
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
import { Throttle } from '@nestjs/throttler';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { UsersService } from '../users/users.service';
import { ChatGateway } from './chat.gateway';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatService } from './chat.service';
import { CreateChatRoomDto } from './dto/create-chat-room.dto';
import { CreateMessageDto } from './dto/create-message.dto';
import { UpdateChatRoomDto } from './dto/update-chat-room.dto';

type RequestUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

@Controller('chat')
@UseGuards(JwtAuthGuard, RolesGuard)
export class ChatController {
  constructor(
    private readonly chatService: ChatService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly chatGateway: ChatGateway,
    private readonly usersService: UsersService,
  ) {}

  @Get('rooms')
  @Roles(AllianceRole.R2)
  async listRooms(@Req() req: { user: RequestUser }) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.chatRoomsService.listForAlliance(user.allianceName);
  }

  @Post('rooms')
  @Roles(AllianceRole.R5)
  async createRoom(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateChatRoomDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.chatRoomsService.createRoom(
      user.allianceName,
      dto.title,
      dto.sortOrder,
    );
  }

  @Patch('rooms/:roomId')
  @Roles(AllianceRole.R5)
  async updateRoom(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
    @Body() dto: UpdateChatRoomDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    return this.chatRoomsService.updateRoom(roomId, user.allianceName, {
      title: dto.title,
      sortOrder: dto.sortOrder,
      archived: dto.archived,
    });
  }

  @Delete('rooms/:roomId')
  @Roles(AllianceRole.R5)
  async deleteRoom(
    @Req() req: { user: RequestUser },
    @Param('roomId') roomId: string,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const user = await this.usersService.findById(req.user.userId);
    if (!user) {
      throw new BadRequestException('User not found');
    }
    await this.chatRoomsService.deleteRoom(roomId, user.allianceName);
    return { success: true };
  }

  @Get('messages')
  @Roles(AllianceRole.R2)
  async getRecentMessages(
    @Req() req: { user: RequestUser },
    @Query('roomId') roomId?: string,
    @Query('before') before?: string,
    @Query('limit') limitRaw?: string,
  ) {
    if (!roomId?.trim()) {
      throw new BadRequestException('roomId query parameter is required');
    }
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const parsed = limitRaw ? Number.parseInt(limitRaw, 10) : undefined;
    const limit = Number.isFinite(parsed) ? parsed : undefined;
    return this.chatService.getRecentMessages(req.user.userId, roomId, {
      before,
      limit,
    });
  }

  @Post('messages')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 8, ttl: 10_000 } })
  async createMessage(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateMessageDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const message = await this.chatService.createMessage({
      roomId: dto.roomId,
      text: dto.text,
      author: req.user,
    });
    this.chatGateway.broadcastNewMessage(dto.roomId, message);
    return message;
  }
}
