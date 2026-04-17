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
  getRecentMessages(
    @Req() _req: { user: RequestUser },
    @Query('allianceId') allianceId?: string,
  ) {
    return this.chatService.getRecentMessages(allianceId ?? 'OBZHORY');
  }

  @Post('messages')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 8, ttl: 10_000 } })
  async createMessage(
    @Req() req: { user: RequestUser },
    @Body() dto: CreateMessageDto,
  ) {
    const allianceId = dto.allianceId ?? 'OBZHORY';
    const message = await this.chatService.createMessage({
      allianceId,
      text: dto.text,
      author: req.user,
    });
    this.chatGateway.broadcastNewMessage(allianceId, message);
    return message;
  }
}
