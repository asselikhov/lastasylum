import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { TeamPresenceGateway } from './team-presence.gateway';
import { TeamsService } from './teams.service';

describe('TeamPresenceGateway', () => {
  let gateway: TeamPresenceGateway;
  const getUserPresenceBroadcastRow = jest.fn();
  const emit = jest.fn();
  const to = jest.fn().mockReturnValue({ emit });

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        TeamPresenceGateway,
        {
          provide: TeamsService,
          useValue: { getUserPresenceBroadcastRow },
        },
        { provide: JwtService, useValue: {} },
        { provide: ConfigService, useValue: {} },
      ],
    }).compile();
    gateway = module.get(TeamPresenceGateway);
    gateway.server = { to } as never;
  });

  it('broadcastPresence emits team:presence to squad room', () => {
    gateway.broadcastPresence('team-1', {
      userId: 'u1',
      presenceStatus: 'ingame',
      lastPresenceAt: '2026-01-01T00:00:00.000Z',
    });
    expect(to).toHaveBeenCalledWith('team:team-1');
    expect(emit).toHaveBeenCalledWith('team:presence', {
      userId: 'u1',
      presenceStatus: 'ingame',
      lastPresenceAt: '2026-01-01T00:00:00.000Z',
    });
  });

  it('broadcastUserPresence resolves squad team id from roster row', async () => {
    getUserPresenceBroadcastRow.mockResolvedValue({
      userId: 'u1',
      playerTeamId: 'team-1',
      presenceStatus: 'online',
      lastPresenceAt: null,
      username: 'alice',
      teamRole: 'R3',
      isLeader: false,
    });
    await gateway.broadcastUserPresence('u1');
    expect(getUserPresenceBroadcastRow).toHaveBeenCalledWith('u1');
    expect(to).toHaveBeenCalledWith('team:team-1');
    expect(emit).toHaveBeenCalledWith('team:presence', {
      userId: 'u1',
      presenceStatus: 'online',
      lastPresenceAt: null,
      username: 'alice',
      teamRole: 'R3',
      isLeader: false,
    });
  });

  it('broadcastUserPresence skips when user has no squad team', async () => {
    getUserPresenceBroadcastRow.mockResolvedValue({
      userId: 'u1',
      playerTeamId: null,
      presenceStatus: 'away',
      lastPresenceAt: null,
    });
    await gateway.broadcastUserPresence('u1');
    expect(to).not.toHaveBeenCalled();
  });
});
