import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { TeamsService } from './teams.service';
import { PlayerTeam } from './schemas/player-team.schema';
import { TeamJoinRequest } from './schemas/team-join-request.schema';
import { User } from './schemas/user.schema';
import { GameIdentitiesService } from './game-identities.service';
import { ChatRoomsService } from '../chat/chat-rooms.service';

describe('TeamsService server scoping', () => {
  let teams: TeamsService;
  const teamOn109 = new Types.ObjectId();

  const teamModel = {
    find: jest.fn().mockReturnValue({
      sort: () => ({
        limit: () => ({
          lean: () => ({
            exec: async () => [
              {
                _id: teamOn109,
                tag: 'ABC',
                displayName: 'Alpha',
              },
            ],
          }),
        }),
      }),
    }),
    findById: jest.fn(),
  };

  const userModel = {
    findById: jest.fn().mockReturnValue({
      exec: async () => null,
    }),
    aggregate: jest.fn().mockReturnValue({
      exec: async () => [{ _id: teamOn109 }],
    }),
  };

  const gameIdentities = {
    resolveSenderServerNumber: jest.fn(),
    collectServerNumbersForTeam: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamsService,
        { provide: getModelToken(PlayerTeam.name), useValue: teamModel },
        { provide: getModelToken(TeamJoinRequest.name), useValue: {} },
        { provide: getModelToken(User.name), useValue: userModel },
        { provide: ChatRoomsService, useValue: {} },
        { provide: GameIdentitiesService, useValue: gameIdentities },
      ],
    }).compile();
    teams = moduleRef.get(TeamsService);
  });

  it('searchTeams limits to teams on requester active server', async () => {
    const requesterId = new Types.ObjectId().toString();
    userModel.findById.mockReturnValue({
      exec: async () => ({
        gameIdentities: [{ serverNumber: 109, gameNickname: 'n' }],
        activeGameIdentityId: null,
      }),
    });
    gameIdentities.resolveSenderServerNumber.mockReturnValue(109);

    const rows = await teams.searchTeams('alp', requesterId, 20);

    expect(rows).toHaveLength(1);
    expect(rows[0].id).toBe(teamOn109.toString());
    expect(teamModel.find).toHaveBeenCalledWith(
      expect.objectContaining({
        _id: { $in: [teamOn109] },
      }),
    );
  });

  it('searchTeams returns empty when requester has no active server', async () => {
    userModel.findById.mockReturnValue({
      exec: async () => ({ gameIdentities: [] }),
    });
    gameIdentities.resolveSenderServerNumber.mockReturnValue(null);

    const rows = await teams.searchTeams('x', new Types.ObjectId().toString());

    expect(rows).toEqual([]);
    expect(teamModel.find).not.toHaveBeenCalled();
  });
});
