import { Test } from '@nestjs/testing';
import { Types } from 'mongoose';
import { PushNotificationsService } from './push-notifications.service';
import { UsersService } from '../users/users.service';

describe('PushNotificationsService', () => {
  const collectPushTokensForAlliance = jest.fn();
  let service: PushNotificationsService;
  const prevJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

  afterAll(() => {
    if (prevJson === undefined) {
      delete process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    } else {
      process.env.FIREBASE_SERVICE_ACCOUNT_JSON = prevJson;
    }
  });

  beforeEach(async () => {
    jest.clearAllMocks();
    delete process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
    const moduleRef = await Test.createTestingModule({
      providers: [
        PushNotificationsService,
        {
          provide: UsersService,
          useValue: { collectPushTokensForAlliance },
        },
      ],
    }).compile();
    service = moduleRef.get(PushNotificationsService);
    await service.onModuleInit();
  });

  it('does not collect tokens when Firebase is not configured', async () => {
    collectPushTokensForAlliance.mockResolvedValue(['t1']);
    await service.notifyAllianceChatMessage({
      allianceId: 'ally',
      excludeUserId: new Types.ObjectId().toHexString(),
      title: 'Hi',
      body: 'Body',
      data: { k: 'v' },
    });
    expect(collectPushTokensForAlliance).not.toHaveBeenCalled();
  });
});
