import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { UsersService } from './users.service';
import { User } from './schemas/user.schema';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { AllianceRegistryService } from './alliance-registry.service';
import { TeamsService } from './teams.service';
import { StickerAccessService } from './sticker-access.service';

describe('UsersService', () => {
  const updateOneExec = jest.fn().mockResolvedValue({ modifiedCount: 1 });
  const updateOne = jest.fn().mockReturnValue({ exec: updateOneExec });
  const execFindById = jest.fn();
  const findById = jest.fn().mockReturnValue({ exec: execFindById });
  const execCollect = jest.fn();
  const findForAlliance = jest.fn().mockReturnValue({
    select: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({ exec: execCollect }),
    }),
  });

  let usersService: UsersService;

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        UsersService,
        { provide: AllianceRegistryService, useValue: {} },
        { provide: TeamsService, useValue: {} },
        { provide: StickerAccessService, useValue: {} },
        {
          provide: getModelToken(User.name),
          useValue: {
            findById,
            updateOne,
            find: findForAlliance,
          },
        },
      ],
    }).compile();
    usersService = moduleRef.get(UsersService);
  });

  describe('registerPushToken', () => {
    it('does nothing when user missing', async () => {
      execFindById.mockResolvedValue(null);
      await usersService.registerPushToken('u1', 'tok');
      expect(updateOne).not.toHaveBeenCalled();
    });

    it('merges unique tokens and trims', async () => {
      execFindById.mockResolvedValue({
        _id: 'u1',
        pushFcmTokens: ['a'],
      });
      await usersService.registerPushToken('u1', '  b  ');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        { $set: { pushFcmTokens: ['a', 'b'] } },
      );
    });

    it('dedupes identical token', async () => {
      execFindById.mockResolvedValue({
        _id: 'u1',
        pushFcmTokens: ['x'],
      });
      await usersService.registerPushToken('u1', 'x');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        { $set: { pushFcmTokens: ['x'] } },
      );
    });

    it('keeps at most 10 tokens', async () => {
      const many = Array.from({ length: 10 }, (_, i) => `t${i}`);
      execFindById.mockResolvedValue({
        _id: 'u1',
        pushFcmTokens: many,
      });
      await usersService.registerPushToken('u1', 'new');
      const setArg = updateOne.mock.calls[0][1] as {
        $set: { pushFcmTokens: string[] };
      };
      expect(setArg.$set.pushFcmTokens).toHaveLength(10);
      expect(setArg.$set.pushFcmTokens[9]).toBe('new');
    });
  });

  describe('clearPushTokens', () => {
    it('clears token array', async () => {
      await usersService.clearPushTokens('u1');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        { $set: { pushFcmTokens: [] } },
      );
    });
  });

  describe('updatePresence', () => {
    it('ingame updates overlay timestamp only', async () => {
      await usersService.updatePresence('u1', ' ingame ');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        {
          $set: {
            presenceStatus: 'ingame',
            lastPresenceAt: expect.any(Date),
          },
        },
      );
    });

    it('non-ingame updates app activity without touching overlay timestamp', async () => {
      const long = ` ${'p'.repeat(40)} `;
      await usersService.updatePresence('u1', long);
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        {
          $set: {
            presenceStatus: 'p'.repeat(32),
            lastAppActiveAt: expect.any(Date),
          },
        },
      );
    });
  });

  describe('collectPushTokensForExcavationAlert', () => {
    it('excludes users in fresh ingame overlay and opted out', async () => {
      const exclude = new Types.ObjectId();
      execCollect.mockResolvedValue([{ pushFcmTokens: ['t1'] }]);
      const out = await usersService.collectPushTokensForExcavationAlert(
        'pt:507f1f77bcf86cd799439011',
        exclude.toHexString(),
      );
      expect(out).toEqual(['t1']);
      const call = findForAlliance.mock.calls[0][0] as Record<string, unknown>;
      expect(call.playerTeamId).toBeDefined();
      expect(call.excavationPushEnabled).toEqual({ $ne: false });
      expect(call.$or).toBeDefined();
    });
  });

  describe('collectPushTokensForAlliance', () => {
    it('returns empty when excludeUserId is not a valid ObjectId', async () => {
      const out = await usersService.collectPushTokensForAlliance(
        'ally',
        'not-an-object-id',
      );
      expect(out).toEqual([]);
      expect(findForAlliance).not.toHaveBeenCalled();
    });

    it('flattens tokens from active alliance members', async () => {
      const exclude = new Types.ObjectId();
      execCollect.mockResolvedValue([
        { pushFcmTokens: ['a', 'b'] },
        { pushFcmTokens: ['c'] },
      ]);
      const out = await usersService.collectPushTokensForAlliance(
        'ally1',
        exclude.toHexString(),
      );
      expect(out).toEqual(['a', 'b', 'c']);
      expect(findForAlliance).toHaveBeenCalledWith({
        allianceName: 'ally1',
        membershipStatus: TeamMembershipStatus.ACTIVE,
        _id: { $ne: exclude },
        pushFcmTokens: { $exists: true, $ne: [] },
      });
    });
  });
});
