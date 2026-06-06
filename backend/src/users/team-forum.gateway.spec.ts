import { TeamForumGateway } from './team-forum.gateway';

describe('TeamForumGateway.broadcastTopicRead', () => {
  it('emits topic:read to topic room', () => {
    const emit = jest.fn();
    const gateway = new TeamForumGateway({} as any, {} as any, {} as any, {} as any);
    gateway.server = {
      to: jest.fn().mockReturnValue({ emit }),
    } as any;

    gateway.broadcastTopicRead(
      'team1',
      'topic1',
      'user1',
      'msg1',
    );

    expect(gateway.server.to).toHaveBeenCalledWith('forum:team1:topic1');
    expect(emit).toHaveBeenCalledWith('topic:read', {
      teamId: 'team1',
      topicId: 'topic1',
      userId: 'user1',
      messageId: 'msg1',
    });
  });
});

describe('TeamForumGateway personal fanout dedup', () => {
  it('does not personal-fanout users already in topic room', async () => {
    const emit = jest.fn();
    const to = jest.fn().mockReturnValue({ emit });
    const listSquadMemberUserIds = jest
      .fn()
      .mockResolvedValue(['user-in-room', 'user-outside']);
    const gateway = new TeamForumGateway(
      {} as any,
      { listSquadMemberUserIds } as any,
      {} as any,
      {} as any,
    );
    gateway.server = {
      to,
      adapter: {
        rooms: new Map([
          ['forum:team1:topic1', new Set(['socket-in-room'])],
        ]),
      },
      sockets: new Map([
        [
          'socket-in-room',
          { data: { user: { userId: 'user-in-room' } } },
        ],
      ]),
    } as any;

    gateway.broadcastNewMessageWithFanout(
      'team1',
      'topic1',
      {
        id: 'msg1',
        topicId: 'topic1',
        teamId: 'team1',
        senderUserId: 'sender1',
        senderUsername: 'Sender',
        senderTelegramUsername: null,
        senderRole: 'R1',
        senderTeamTag: null,
        senderServerNumber: null,
        text: 'hi',
        replyToMessageId: null,
        replyTo: null,
        editedAt: null,
        deletedAt: null,
        deletedByUserId: null,
        imageRelativeUrl: null,
        imageRelativeUrls: [],
        fileRelativeUrl: null,
        fileFilename: null,
        forwardedFrom: null,
        reactions: [],
        clientMessageId: null,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
      'sender1',
    );

    await new Promise((r) => setTimeout(r, 20));

    const personalTargets = to.mock.calls
      .map((call) => call[0] as string)
      .filter((room) => room.startsWith('user:'));
    expect(personalTargets).toEqual(['user:user-outside']);
    expect(personalTargets).not.toContain('user:user-in-room');
    expect(personalTargets).not.toContain('user:sender1');
  });
});
