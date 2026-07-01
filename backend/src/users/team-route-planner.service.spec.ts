import { BadRequestException } from '@nestjs/common';
import { TeamRoutePlannerService } from './team-route-planner.service';

describe('TeamRoutePlannerService', () => {
  const teams = {
    getTeamIfMemberOrThrow: jest.fn(),
    assertSquadOfficerOrThrow: jest.fn(),
  } as any;

  const model = {
    findOne: jest.fn(),
    findOneAndUpdate: jest.fn(),
  } as any;

  const service = new TeamRoutePlannerService(model, teams);

  it('rejects invalid route type on replace', async () => {
    await expect(
      service.replaceRoutes('507f1f77bcf86cd799439011', 'user1', {
        routes: [{ id: 'r1', name: 'Test', type: 'bad', createdAtMs: 1 }],
      }),
    ).rejects.toBeInstanceOf(BadRequestException);
  });
});
