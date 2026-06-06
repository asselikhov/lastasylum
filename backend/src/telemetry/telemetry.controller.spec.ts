import { Test, TestingModule } from '@nestjs/testing';
import { DeliveryBatchDto } from './dto/delivery-batch.dto';
import { TelemetryController } from './telemetry.controller';
import { TelemetryService } from './telemetry.service';

describe('TelemetryController', () => {
  let controller: TelemetryController;
  const ingestDeliveryBatch = jest.fn().mockResolvedValue({ inserted: 1 });

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      controllers: [TelemetryController],
      providers: [
        {
          provide: TelemetryService,
          useValue: { ingestDeliveryBatch },
        },
      ],
    }).compile();

    controller = module.get<TelemetryController>(TelemetryController);
  });

  it('forwards delivery batch to telemetry service with authenticated user', async () => {
    const dto: DeliveryBatchDto = {
      samples: [
        {
          spanType: 'chat_send_to_socket',
          correlationId: 'client-msg-1',
          durationMs: 42,
          outcome: 'ok',
        },
      ],
    };

    await expect(
      controller.submitDelivery({ user: { userId: 'user-1' } }, dto),
    ).resolves.toEqual({ inserted: 1 });

    expect(ingestDeliveryBatch).toHaveBeenCalledWith('user-1', dto);
  });
});
