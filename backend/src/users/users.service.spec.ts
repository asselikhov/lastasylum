import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { UsersService } from './users.service';
import { User } from './schemas/user.schema';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { AllianceRegistryService } from './alliance-registry.service';
import { TeamsService } from './teams.service';
import { StickerAccessService } from './sticker-access.service';
import { GameIdentitiesService } from './game-identities.service';

describe('UsersService', () => {
  const reconcileSquadTeamBindingForUser = jest.fn();
  const resolveSquadTeamIdForUser = jest.fn();
  const listSquadMemberUserIds = jest.fn();
  const updateOneExec = jest.fn().mockResolvedValue({ modifiedCount: 1 });
  const updateOne = jest.fn().mockReturnValue({ exec: updateOneExec });
  const execFindById = jest.fn();
  const execFindByIdLean = jest.fn();
  const findById = jest.fn().mockImplementation(() => ({
    exec: execFindById,
    select: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({ exec: execFindByIdLean }),
    }),
  }));
  const execCollect = jest.fn();
  const execFindIngame = jest.fn();
  const findForAlliance = jest.fn().mockReturnValue({
    select: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({ exec: execCollect }),
    }),
  });
  const findIngameList = jest.fn().mockReturnValue({
    select: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({ exec: execFindIngame }),
    }),
  });

  let usersService: UsersService;

  beforeEach(async () => {
    jest.clearAllMocks();
    reconcileSquadTeamBindingForUser.mockImplementation((id: string) =>
      findById(id).exec(),
    );
    resolveSquadTeamIdForUser.mockResolvedValue(null);
    listSquadMemberUserIds.mockResolvedValue([]);
    const moduleRef = await Test.createTestingModule({
      providers: [
        UsersService,
        {
          provide: TeamsService,
          useValue: {
            reconcileSquadTeamBindingForUser,
            resolveSquadTeamIdForUser,
            listSquadMemberUserIds,
            getPlayerTeamProfileFields: jest.fn().mockResolvedValue({
              playerTeamId: null,
              playerTeamTag: null,
              playerTeamDisplayName: null,
              playerTeamLeaderUserId: null,
              isPlayerTeamLeader: false,
              pendingPlayerTeamJoinRequests: 0,
              playerTeamSquadRole: null,
            }),
          },
        },
        {
          provide: AllianceRegistryService,
          useValue: {
            resolveFlagsByAllianceCode: jest.fn().mockResolvedValue({
              alliancePublicId: 'pub',
              overlayTabVisible: true,
            }),
          },
        },
        {
          provide: StickerAccessService,
          useValue: {
            listEnabledPackKeysForUser: jest.fn().mockResolvedValue([]),
          },
        },
        {
          provide: GameIdentitiesService,
          useValue: {
            ensureMigrated: jest.fn((user: unknown) => Promise.resolve(user)),
            buildSafeIdentities: jest.fn().mockResolvedValue([]),
            getActiveIdentity: jest.fn().mockReturnValue(null),
          },
        },
        {
          provide: getModelToken(User.name),
          useValue: {
            findById,
            updateOne,
            find: jest.fn((filter: Record<string, unknown>) => {
              if (filter?.presenceStatus === 'ingame') {
                return findIngameList(filter);
              }
              return findForAlliance(filter);
            }),
          },
        },
      ],
    }).compile();
    usersService = moduleRef.get(UsersService);
  });

  describe('registerPushToken', () => {
    const pushUserId = new Types.ObjectId().toHexString();

    it('does nothing when user missing', async () => {
      execFindById.mockResolvedValue(null);
      await usersService.registerPushToken(pushUserId, 'tok');
      expect(updateOne).not.toHaveBeenCalled();
    });

    it('merges unique tokens and trims', async () => {
      execFindById.mockResolvedValue({
        _id: pushUserId,
        pushFcmTokens: ['a'],
      });
      await usersService.registerPushToken(pushUserId, '  b  ');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: pushUserId },
        { $set: { pushFcmTokens: ['a', 'b'] } },
      );
    });

    it('dedupes identical token', async () => {
      execFindById.mockResolvedValue({
        _id: pushUserId,
        pushFcmTokens: ['x'],
      });
      await usersService.registerPushToken(pushUserId, 'x');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: pushUserId },
        { $set: { pushFcmTokens: ['x'] } },
      );
    });

    it('keeps at most 10 tokens', async () => {
      const many = Array.from({ length: 10 }, (_, i) => `t${i}`);
      execFindById.mockResolvedValue({
        _id: pushUserId,
        pushFcmTokens: many,
      });
      await usersService.registerPushToken(pushUserId, 'new');
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

  describe('recordAppVersionFromUserAgent', () => {
    it('writes when version changed', async () => {
      const leanExec = jest.fn().mockResolvedValue({
        lastAppVersionName: '1.0.0',
        lastAppVersionCode: 1,
      });
      findById.mockReturnValueOnce({
        select: jest.fn().mockReturnValue({
          lean: jest.fn().mockReturnValue({ exec: leanExec }),
        }),
      });
      await usersService.recordAppVersionFromUserAgent(
        'u1',
        'SquadRelay-Android/1.4.2 (42)',
      );
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        {
          $set: {
            lastAppVersionName: '1.4.2',
            lastAppVersionCode: 42,
            lastAppVersionReportedAt: expect.any(Date),
          },
        },
      );
    });

    it('skips write when version unchanged', async () => {
      const leanExec = jest.fn().mockResolvedValue({
        lastAppVersionName: '1.4.2',
        lastAppVersionCode: 42,
      });
      findById.mockReturnValueOnce({
        select: jest.fn().mockReturnValue({
          lean: jest.fn().mockReturnValue({ exec: leanExec }),
        }),
      });
      updateOne.mockClear();
      await usersService.recordAppVersionFromUserAgent(
        'u1',
        'SquadRelay-Android/1.4.2 (42)',
      );
      expect(updateOne).not.toHaveBeenCalled();
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

    it('online does not downgrade fresh ingame overlay presence', async () => {
      execFindByIdLean.mockResolvedValue({
        presenceStatus: 'ingame',
        lastPresenceAt: new Date(),
      });
      await usersService.updatePresence('u1', 'online');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        { $set: { lastAppActiveAt: expect.any(Date) } },
      );
      const downgraded = updateOne.mock.calls.some(
        (call) =>
          (call[1] as { $set?: { presenceStatus?: string } })?.$set
            ?.presenceStatus === 'online',
      );
      expect(downgraded).toBe(false);
    });

    it('away does not downgrade fresh ingame overlay presence', async () => {
      execFindByIdLean.mockResolvedValue({
        presenceStatus: 'ingame',
        lastPresenceAt: new Date(),
      });
      await usersService.updatePresence('u1', 'away');
      expect(updateOne).toHaveBeenCalledWith(
        { _id: 'u1' },
        { $set: { lastAppActiveAt: expect.any(Date) } },
      );
      const downgraded = updateOne.mock.calls.some(
        (call) =>
          (call[1] as { $set?: { presenceStatus?: string } })?.$set
            ?.presenceStatus === 'away',
      );
      expect(downgraded).toBe(false);
    });

    it('non-ingame updates app activity without touching overlay timestamp', async () => {
      execFindByIdLean.mockResolvedValue({
        presenceStatus: 'away',
        lastPresenceAt: null,
      });
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

  describe('isOverlayIngameNow', () => {
    it('returns false for invalid user id', async () => {
      await expect(usersService.isOverlayIngameNow('not-an-id')).resolves.toBe(
        false,
      );
    });

    it('returns true when ingame ping is fresh', async () => {
      execFindByIdLean.mockResolvedValue({
        presenceStatus: 'ingame',
        lastPresenceAt: new Date(),
        membershipStatus: TeamMembershipStatus.ACTIVE,
      });
      await expect(
        usersService.isOverlayIngameNow('507f1f77bcf86cd799439011'),
      ).resolves.toBe(true);
    });

    it('returns false when ingame ping is stale', async () => {
      execFindByIdLean.mockResolvedValue({
        presenceStatus: 'ingame',
        lastPresenceAt: new Date(Date.now() - 121_000),
        membershipStatus: TeamMembershipStatus.ACTIVE,
      });
      await expect(
        usersService.isOverlayIngameNow('507f1f77bcf86cd799439011'),
      ).resolves.toBe(false);
    });

    it('returns false when status is not ingame', async () => {
      execFindByIdLean.mockResolvedValue({
        presenceStatus: 'online',
        lastPresenceAt: new Date(),
        membershipStatus: TeamMembershipStatus.ACTIVE,
      });
      await expect(
        usersService.isOverlayIngameNow('507f1f77bcf86cd799439011'),
      ).resolves.toBe(false);
    });
  });

  describe('listOverlayIngameTeammateIds', () => {
    it('returns teammate ids with fresh ingame overlay', async () => {
      const teamId = new Types.ObjectId();
      const mate = new Types.ObjectId();
      const senderId = '507f1f77bcf86cd799439011';
      resolveSquadTeamIdForUser.mockResolvedValue(teamId.toString());
      listSquadMemberUserIds.mockResolvedValue([senderId, mate.toString()]);
      execFindByIdLean.mockResolvedValue({
        membershipStatus: TeamMembershipStatus.ACTIVE,
      });
      execFindIngame.mockResolvedValue([{ _id: mate }]);
      const out = await usersService.listOverlayIngameTeammateIds(senderId);
      expect(out).toEqual([mate.toString()]);
      const call = findIngameList.mock.calls[0][0] as Record<string, unknown>;
      expect(call.presenceStatus).toBe('ingame');
      expect(call._id).toEqual({ $in: [mate] });
    });

    it('returns empty when sender has no team', async () => {
      resolveSquadTeamIdForUser.mockResolvedValue(null);
      const out = await usersService.listOverlayIngameTeammateIds(
        '507f1f77bcf86cd799439011',
      );
      expect(out).toEqual([]);
      expect(findIngameList).not.toHaveBeenCalled();
    });
  });

  describe('collectPushTokensForExcavationAlert', () => {
    it('excludes users in fresh ingame overlay and opted out', async () => {
      const exclude = new Types.ObjectId();
      const allyId = new Types.ObjectId();
      execCollect.mockResolvedValue([
        {
          _id: allyId,
          pushFcmTokens: ['t1'],
          presenceStatus: 'online',
          lastPresenceAt: new Date(0),
        },
      ]);
      const out = await usersService.collectPushTokensForExcavationAlert(
        'pt:507f1f77bcf86cd799439011',
        exclude.toHexString(),
      );
      expect(out).toEqual(['t1']);
      const call = findForAlliance.mock.calls[0][0] as Record<string, unknown>;
      expect(call.excavationPushEnabled).toBeUndefined();
      expect(call.$or).toBeDefined();
    });

    it('skips push tokens for overlay-ingame allies', async () => {
      const exclude = new Types.ObjectId();
      const ingameAlly = new Types.ObjectId();
      const offlineAlly = new Types.ObjectId();
      execCollect.mockResolvedValue([
        {
          _id: ingameAlly,
          pushFcmTokens: ['skip'],
          presenceStatus: 'ingame',
          lastPresenceAt: new Date(),
        },
        {
          _id: offlineAlly,
          pushFcmTokens: ['keep'],
          presenceStatus: 'online',
          lastPresenceAt: new Date(0),
        },
      ]);
      const out = await usersService.collectPushTokensForExcavationAlert(
        'ally1',
        exclude.toHexString(),
      );
      expect(out).toEqual(['keep']);
    });
  });

  describe('collectPushTokensForGameEvent', () => {
    it('skips tokens for users who opted out of the event', async () => {
      const exclude = new Types.ObjectId();
      execCollect.mockResolvedValue([
        {
          _id: new Types.ObjectId(),
          pushFcmTokens: ['skip'],
          gameEventPushEnabled: { pve_gather_5m: false },
          presenceStatus: 'online',
          lastPresenceAt: new Date(0),
        },
        {
          _id: new Types.ObjectId(),
          pushFcmTokens: ['keep'],
          gameEventPushEnabled: {},
          presenceStatus: 'online',
          lastPresenceAt: new Date(0),
        },
      ]);
      const out = await usersService.collectPushTokensForGameEvent(
        'ally1',
        'pve_gather_5m',
        exclude.toHexString(),
      );
      expect(out).toEqual(['keep']);
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

  describe('toSafeUser', () => {
    const baseUser = () => ({
      _id: new Types.ObjectId(),
      username: 'u',
      email: 'u@test.com',
      role: 'MEMBER',
      allianceName: 'ally',
      membershipStatus: TeamMembershipStatus.ACTIVE,
      excavationPushEnabled: true,
      pushFcmTokens: [],
      lastProfileReconcileAt: null as Date | null,
    });

    it('skips reconcile when lastProfileReconcileAt is recent', async () => {
      reconcileSquadTeamBindingForUser.mockClear();
      const user = {
        ...baseUser(),
        lastProfileReconcileAt: new Date(),
      };
      await usersService.toSafeUser(user as never);
      expect(reconcileSquadTeamBindingForUser).not.toHaveBeenCalled();
    });

    it('reconciles when lastProfileReconcileAt is missing', async () => {
      reconcileSquadTeamBindingForUser.mockClear();
      reconcileSquadTeamBindingForUser.mockResolvedValue(baseUser());
      const user = baseUser();
      await usersService.toSafeUser(user as never);
      expect(reconcileSquadTeamBindingForUser).toHaveBeenCalledTimes(1);
      expect(updateOne).toHaveBeenCalled();
    });

    it('returns cached SafeUser within TTL without second reconcile', async () => {
      reconcileSquadTeamBindingForUser.mockClear();
      reconcileSquadTeamBindingForUser.mockResolvedValue(baseUser());
      const user = baseUser();
      await usersService.toSafeUser(user as never);
      await usersService.toSafeUser(user as never);
      expect(reconcileSquadTeamBindingForUser).toHaveBeenCalledTimes(1);
    });
  });
});
