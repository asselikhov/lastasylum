import { buildMessageReactionBroadcastPayload, filterPersonalChatFanoutUserIds } from './chat-realtime-broadcast.util';

describe('buildMessageReactionBroadcastPayload', () => {
  it('maps userIds to count without reactedByMe', () => {
    const payload = buildMessageReactionBroadcastPayload({
      messageId: 'msg1',
      roomId: 'room1',
      reactions: [
        { emoji: '👍', userIds: ['u1', 'u2'] },
        { emoji: '❤️', userIds: [] },
      ],
    });
    expect(payload).toEqual({
      messageId: 'msg1',
      roomId: 'room1',
      reactions: [
        { emoji: '👍', count: 2, userIds: ['u1', 'u2'] },
        { emoji: '❤️', count: 0, userIds: [] },
      ],
    });
    expect(JSON.stringify(payload)).not.toContain('reactedByMe');
  });
});

describe('filterPersonalChatFanoutUserIds', () => {
  it('excludes sender and users already in chat room socket', () => {
    const inRoom = new Set(['u1', 'u2']);
    expect(
      filterPersonalChatFanoutUserIds(
        ['u1', 'u2', 'u3', 'u4'],
        inRoom,
        'sender',
      ),
    ).toEqual(['u3', 'u4']);
  });

  it('keeps eligible overlay users not joined to chat room', () => {
    const inRoom = new Set<string>();
    expect(
      filterPersonalChatFanoutUserIds(['overlay1', 'overlay2'], inRoom),
    ).toEqual(['overlay1', 'overlay2']);
  });

  it('skips blank ids', () => {
    expect(
      filterPersonalChatFanoutUserIds(['', '  ', 'u1'], new Set()),
    ).toEqual(['u1']);
  });
});
