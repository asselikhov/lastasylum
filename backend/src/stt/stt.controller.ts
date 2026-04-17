import {
  BadRequestException,
  Controller,
  Post,
  Req,
  UploadedFile,
  UseGuards,
  UseInterceptors,
} from '@nestjs/common';
import { Throttle } from '@nestjs/throttler';
import { FileInterceptor } from '@nestjs/platform-express';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { Roles } from '../common/decorators/roles.decorator';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { RolesGuard } from '../common/guards/roles.guard';
import { SttService } from './stt.service';

type RequestUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

@Controller('stt')
@UseGuards(JwtAuthGuard, RolesGuard)
export class SttController {
  constructor(private readonly sttService: SttService) {}

  @Post('transcribe')
  @Roles(AllianceRole.R2)
  @Throttle({ default: { limit: 20, ttl: 60_000 } })
  @UseInterceptors(FileInterceptor('audio'))
  async transcribe(
    @Req() req: { user: RequestUser },
    @UploadedFile() file?: Express.Multer.File,
  ) {
    if (!file) {
      throw new BadRequestException('Audio file is required');
    }

    const text = await this.sttService.transcribe(file);

    return {
      text: `${req.user.username}: ${text}`,
    };
  }
}
