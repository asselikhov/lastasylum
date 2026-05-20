import { Types } from 'mongoose';
import { GameIdentitiesService } from './game-identities.service';

describe('GameIdentitiesService admin pagination', () => {
  const userModel = {
    countDocuments: jest.fn(),
    aggregate: jest.fn(),
  };

  const service = new GameIdentitiesService(userModel as never, {} as never);

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('caps limit at 200 and uses skip in aggregation', async () => {
    userModel.countDocuments.mockResolvedValue(0);
    userModel.aggregate.mockReturnValue({
      exec: jest.fn().mockResolvedValue([{ data: [], meta: [] }]),
    });

    await service.listUsersForAdminByServer({
      skip: 10,
      limit: 999,
      serverNumber: 42,
    });

    const pipeline = userModel.aggregate.mock.calls[0][0] as Array<{
      $facet?: { data: Array<{ $skip?: number; $limit?: number }> };
    }>;
    const facet = pipeline.find((s) => s.$facet)?.$facet;
    expect(facet?.data).toEqual([{ $skip: 10 }, { $limit: 200 }]);
  });
});
