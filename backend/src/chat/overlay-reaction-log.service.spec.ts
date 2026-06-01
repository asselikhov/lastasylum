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

describe('OverlayReactionLogService createPersonal reply', () => {
  const teamId = new Types.ObjectId();
  const parentId = new Types.ObjectId();
  const senderId = 'user-replier';
  const targetId = 'user-original';

  function mockSender() {
    return {
      _id: { toString: () => senderId },
      username: 'Replier',
      playerTeamId: teamId,
    };
  }

  function mockTarget() {
    return {
      _id: { toString: () => targetId },
      username: 'Original',
      playerTeamId: teamId,
    };
  }

  function createReplyService(parent: {
    _id: Types.ObjectId;
    visibility: 'personal' | 'broadcast';
    senderUserId: string;
    targetUserId?: string | null;
    reaction: string;
    senderUsername: string;
    targetUsername?: string | null;
    toObject: () => Record<string, unknown>;
  }) {
    const createdDoc = {
      _id: new Types.ObjectId(),
      teamId,
      senderUserId: senderId,
      senderUsername: 'Replier',
      targetUserId: targetId,
      targetUsername: 'Original',
      reaction: 'thumbsup',
      visibility: parent.visibility === 'broadcast' ? 'broadcast' : 'personal',
      replyToLogId: parent._id,
      replyToLog: {
        _id: parent._id,
        reaction: parent.reaction,
        visibility: parent.visibility,
        senderUserId: parent.senderUserId,
        senderUsername: parent.senderUsername,
        targetUserId: parent.targetUserId ?? null,
        targetUsername: parent.targetUsername ?? null,
      },
      reactions: [],
      toObject: jest.fn().mockReturnValue({
        _id: new Types.ObjectId(),
        senderUserId: senderId,
        senderUsername: 'Replier',
        targetUserId: targetId,
        targetUsername: 'Original',
        reaction: 'thumbsup',
        visibility: parent.visibility === 'broadcast' ? 'broadcast' : 'personal',
        replyToLogId: parent._id,
        replyToLog: {
          _id: parent._id,
          reaction: parent.reaction,
          visibility: parent.visibility,
          senderUserId: parent.senderUserId,
          senderUsername: parent.senderUsername,
          targetUserId: parent.targetUserId ?? null,
          targetUsername: parent.targetUsername ?? null,
        },
        createdAt: new Date(),
        reactions: [],
      }),
    };

    const service = Object.create(
      OverlayReactionLogService.prototype,
    ) as OverlayReactionLogService;

    const findOneChain = {
      exec: jest.fn().mockResolvedValueOnce(parent),
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue({ _id: parent._id }),
      }),
    };

    (service as unknown as { logModel: { findOne: jest.Mock; create: jest.Mock } }).logModel =
      {
        findOne: jest.fn().mockReturnValue(findOneChain),
        create: jest.fn().mockResolvedValue(createdDoc),
      };

    (
      service as unknown as {
        visibilityFilter: (userId: string) => Record<string, unknown>;
      }
    ).visibilityFilter = OverlayReactionLogService.prototype[
      'visibilityFilter'
    ].bind(service);

    (
      service as unknown as {
        listRecipientUserIds: jest.Mock;
      }
    ).listRecipientUserIds = jest
      .fn()
      .mockResolvedValue(['user-a', 'user-b', 'user-c']);

    return { service, createdDoc };
  }

  it('reply to personal sets visibility personal and snapshot', async () => {
    const parent = {
      _id: parentId,
      visibility: 'personal' as const,
      senderUserId: targetId,
      targetUserId: senderId,
      reaction: 'heart',
      senderUsername: 'Original',
      targetUsername: 'Replier',
      toObject: () => ({
        _id: parentId,
        visibility: 'personal',
        senderUserId: targetId,
        targetUserId: senderId,
        reaction: 'heart',
        senderUsername: 'Original',
        targetUsername: 'Replier',
      }),
    };
    const { service } = createReplyService(parent);

    const result = await service.createPersonal({
      sender: mockSender() as never,
      target: mockTarget() as never,
      reaction: 'thumbsup',
      replyToLogId: parentId.toString(),
    });

    expect(result.entry.visibility).toBe('personal');
    expect(result.entry.replyToLogId).toBe(parentId.toString());
    expect(result.entry.replyToLog?.reaction).toBe('heart');
    expect(result.recipientUserIds).toEqual(
      expect.arrayContaining(['user-a', 'user-b', 'user-c']),
    );
  });

  it('reply to broadcast sets visibility broadcast for all teammates', async () => {
    const parent = {
      _id: parentId,
      visibility: 'broadcast' as const,
      senderUserId: targetId,
      targetUserId: null,
      reaction: 'heart',
      senderUsername: 'Original',
      targetUsername: null,
      toObject: () => ({
        _id: parentId,
        visibility: 'broadcast',
        senderUserId: targetId,
        targetUserId: null,
        reaction: 'heart',
        senderUsername: 'Original',
        targetUsername: null,
      }),
    };
    const { service } = createReplyService(parent);

    const result = await service.createPersonal({
      sender: mockSender() as never,
      target: mockTarget() as never,
      reaction: 'thumbsup',
      replyToLogId: parentId.toString(),
    });

    expect(result.entry.visibility).toBe('broadcast');
    expect(result.entry.replyToLog?.visibility).toBe('broadcast');
    expect(result.recipientUserIds).toHaveLength(3);
  });
});
