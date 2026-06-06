import {
  Body,
  Controller,
  HttpCode,
  HttpStatus,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { DeliveryBatchDto } from './dto/delivery-batch.dto';
import { TelemetryService } from './telemetry.service';

type RequestUser = {
  userId: string;
};

@Controller('telemetry')
export class TelemetryController {
  constructor(private readonly telemetryService: TelemetryService) {}

  @UseGuards(JwtAuthGuard)
  @Post('delivery')
  @HttpCode(HttpStatus.OK)
  submitDelivery(
    @Req() req: { user: RequestUser },
    @Body() dto: DeliveryBatchDto,
  ) {
    return this.telemetryService.ingestDeliveryBatch(req.user.userId, dto);
  }
}
