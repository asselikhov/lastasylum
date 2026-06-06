import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';
import { DELIVERY_LATENCY_SPAN_TYPES } from '../constants/delivery-latency-span-types';

export type DeliveryLatencyDocument = HydratedDocument<DeliveryLatency>;

const TTL_SECONDS = 7 * 24 * 60 * 60;

@Schema({ timestamps: { createdAt: true, updatedAt: false } })
export class DeliveryLatency {
  @Prop({ required: true, index: true })
  userId!: string;

  @Prop({
    type: String,
    required: true,
    enum: DELIVERY_LATENCY_SPAN_TYPES,
    index: true,
  })
  spanType!: string;

  @Prop({ required: true, trim: true })
  correlationId!: string;

  @Prop({ required: true, min: 0 })
  durationMs!: number;

  @Prop({ required: true, trim: true })
  outcome!: string;

  @Prop({ type: String, default: null, trim: true })
  deviceId!: string | null;
}

export const DeliveryLatencySchema =
  SchemaFactory.createForClass(DeliveryLatency);

DeliveryLatencySchema.index({ createdAt: 1 }, { expireAfterSeconds: TTL_SECONDS });
