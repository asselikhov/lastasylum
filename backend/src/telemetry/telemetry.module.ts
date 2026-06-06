import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { AuthModule } from '../auth/auth.module';
import {
  DeliveryLatency,
  DeliveryLatencySchema,
} from './schemas/delivery-latency.schema';
import { TelemetryController } from './telemetry.controller';
import { TelemetryService } from './telemetry.service';

@Module({
  imports: [
    AuthModule,
    MongooseModule.forFeature([
      { name: DeliveryLatency.name, schema: DeliveryLatencySchema },
    ]),
  ],
  controllers: [TelemetryController],
  providers: [TelemetryService],
})
export class TelemetryModule {}
