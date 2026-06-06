import { BadRequestException, Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { DELIVERY_LATENCY_SPAN_TYPE_SET } from './constants/delivery-latency-span-types';
import {
  DeliveryBatchDto,
  DeliveryLatencySampleDto,
} from './dto/delivery-batch.dto';
import {
  DeliveryLatency,
  DeliveryLatencyDocument,
} from './schemas/delivery-latency.schema';

const CORRELATION_ID_PATTERN = /^[\w\-:.]+$/;
const OUTCOME_PATTERN = /^[\w\-]+$/;

@Injectable()
export class TelemetryService {
  constructor(
    @InjectModel(DeliveryLatency.name)
    private readonly deliveryLatencyModel: Model<DeliveryLatencyDocument>,
  ) {}

  async ingestDeliveryBatch(
    userId: string,
    dto: DeliveryBatchDto,
  ): Promise<{ inserted: number }> {
    const docs = dto.samples.map((sample) =>
      this.toValidatedDocument(userId, sample),
    );
    if (docs.length === 0) {
      return { inserted: 0 };
    }
    await this.deliveryLatencyModel.insertMany(docs, { ordered: false });
    return { inserted: docs.length };
  }

  private toValidatedDocument(
    userId: string,
    sample: DeliveryLatencySampleDto,
  ): Pick<
    DeliveryLatency,
    'userId' | 'spanType' | 'correlationId' | 'durationMs' | 'outcome' | 'deviceId'
  > {
    const spanType = sample.spanType.trim();
    if (!DELIVERY_LATENCY_SPAN_TYPE_SET.has(spanType)) {
      throw new BadRequestException(`Invalid spanType: ${spanType}`);
    }

    const correlationId = sample.correlationId.trim();
    if (
      correlationId.length === 0 ||
      correlationId.length > 128 ||
      !CORRELATION_ID_PATTERN.test(correlationId)
    ) {
      throw new BadRequestException('Invalid correlationId');
    }

    const outcome = sample.outcome.trim();
    if (
      outcome.length === 0 ||
      outcome.length > 32 ||
      !OUTCOME_PATTERN.test(outcome)
    ) {
      throw new BadRequestException('Invalid outcome');
    }

    const durationMs = sample.durationMs;
    if (!Number.isInteger(durationMs) || durationMs < 0 || durationMs > 600_000) {
      throw new BadRequestException('Invalid durationMs');
    }

    const deviceId = sample.deviceId?.trim() || null;
    if (deviceId != null && deviceId.length > 128) {
      throw new BadRequestException('Invalid deviceId');
    }

    return {
      userId,
      spanType,
      correlationId,
      durationMs,
      outcome,
      deviceId,
    };
  }
}
