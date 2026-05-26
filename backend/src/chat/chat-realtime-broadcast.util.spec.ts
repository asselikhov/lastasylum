import { buildMessageReactionBroadcastPayload } from './chat-realtime-broadcast.util';

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
