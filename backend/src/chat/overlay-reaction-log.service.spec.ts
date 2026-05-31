import { Types } from 'mongoose';
import { OverlayReactionLogService } from './overlay-reaction-log.service';

describe('OverlayReactionLogService visibility', () => {
  const service = Object.create(
    OverlayReactionLogService.prototype,
  ) as OverlayReactionLogService;

  const visibilityFilter = (
    service as unknown as { visibilityFilter: (userId: string) => Record<string, unknown> }
  ).visibilityFilter.bind(service);

  it('includes broadcast for any viewer', () => {
    const filter = visibilityFilter('user-a');
    expect(filter).toEqual({
      $or: [
        { visibility: 'broadcast' },
        {
          visibility: 'personal',
          $or: [{ senderUserId: 'user-a' }, { targetUserId: 'user-a' }],
        },
      ],
    });
  });
});

describe('OverlayReactionLogService toggleLogEntryReaction', () => {
  const userId = 'user-viewer';
  const teamId = new Types.ObjectId();
  const logId = new Types.ObjectId();

  function createService(row: {
    _id: Types.ObjectId;
    teamId: Types.ObjectId;
    senderUserId: string;
    senderUsername: string;
    targetUserId?: string | null;
    targetUsername?: string | null;
    reaction: string;
    visibility: 'personal' | 'broadcast';
    createdAt: Date;
    reactions: { emoji: string; userIds: string[] }[];
    save: jest.Mock;
    toObject: jest.Mock;
  }) {
    const service = Object.create(
      OverlayReactionLogService.prototype,
    ) as OverlayReactionLogService;

    (service as unknown as { assertActiveTeamMember: jest.Mock }).assertActiveTeamMember =
      jest.fn().mockResolvedValue({ userId, teamId });

    (service as unknown as { logModel: { findOne: jest.Mock } }).logModel = {
      findOne: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue(row),
      }),
    };

    (service as unknown as { usersService: { listActiveTeamMemberUserIds: jest.Mock } }).usersService =
      {
        listActiveTeamMemberUserIds: jest
          .fn()
          .mockResolvedValue(['user-viewer', 'user-other']),
      };

    return service;
  }

  it('adds emoji reaction for viewer', async () => {
    const row = {
      _id: logId,
      teamId,
      senderUserId: 'user-other',
      senderUsername: 'Other',
      targetUserId: userId,
      targetUsername: 'Viewer',
      reaction: 'heart',
      visibility: 'personal' as const,
      createdAt: new Date('2026-05-29T10:00:00.000Z'),
      reactions: [] as { emoji: string; userIds: string[] }[],
      save: jest.fn().mockResolvedValue(undefined),
      toObject: jest.fn().mockReturnValue({
        _id: logId,
        senderUserId: 'user-other',
        senderUsername: 'Other',
        targetUserId: userId,
        targetUsername: 'Viewer',
        reaction: 'heart',
        visibility: 'personal',
        createdAt: new Date('2026-05-29T10:00:00.000Z'),
        reactions: [{ emoji: '👍', userIds: [userId] }],
      }),
    };
    row.reactions = [];
    const service = createService(row);

    const result = await service.toggleLogEntryReaction(userId, logId.toString(), '👍');

    expect(row.reactions).toEqual([{ emoji: '👍', userIds: [userId] }]);
    expect(row.save).toHaveBeenCalled();
    expect(result.entry.reactions).toEqual([
      { emoji: '👍', count: 1, reactedByMe: true },
    ]);
    expect(result.recipientUserIds).toEqual(
      expect.arrayContaining(['user-other', userId]),
    );
  });

  it('removes emoji reaction when toggled twice', async () => {
    const row = {
      _id: logId,
      teamId,
      senderUserId: 'user-other',
      senderUsername: 'Other',
      targetUserId: userId,
      targetUsername: 'Viewer',
      reaction: 'heart',
      visibility: 'personal' as const,
      createdAt: new Date('2026-05-29T10:00:00.000Z'),
      reactions: [{ emoji: '👍', userIds: [userId] }],
      save: jest.fn().mockResolvedValue(undefined),
      toObject: jest.fn().mockReturnValue({
        _id: logId,
        senderUserId: 'user-other',
        senderUsername: 'Other',
        targetUserId: userId,
        targetUsername: 'Viewer',
        reaction: 'heart',
        visibility: 'personal',
        createdAt: new Date('2026-05-29T10:00:00.000Z'),
        reactions: [],
      }),
    };
    const service = createService(row);

    const result = await service.toggleLogEntryReaction(userId, logId.toString(), '👍');

    expect(row.reactions).toEqual([]);
    expect(result.entry.reactions).toEqual([]);
  });
});
