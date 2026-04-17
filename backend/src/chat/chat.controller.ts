import {
  Body,
  Controller,
  Get,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DEFAULT_ALLIANCE_ID } from '../common/constants/default-alliance-id';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { Throttle } from '@nestjs/throttler';
import { CreateMessageDto } from './dto/create-message.dto';
import { ChatGateway } from './chat.gateway';
import { ChatService } from './chat.service';

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
    private readonly chatGateway: ChatGateway,
  ) {}

  @Get('messages')
  @Roles(AllianceRole.R2)
  async getRecentMessages(
    @Req() req: { user: RequestUser },
    @Query('allianceId') allianceId?: string,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    return this.chatService.getRecentMessages(
      allianceId ?? DEFAULT_ALLIANCE_ID,
    );
  }

  @Post('messages')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 8, ttl: 10_000 } })
  async createMessage(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateMessageDto,
  ) {
    await this.chatService.assertUserMayUseChat(req.user.userId);
    const allianceId = dto.allianceId ?? DEFAULT_ALLIANCE_ID;
    const message = await this.chatService.createMessage({
      allianceId,
      text: dto.text,
      author: req.user,
    });
    this.chatGateway.broadcastNewMessage(allianceId, message);
    return message;
  }
}
