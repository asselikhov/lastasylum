import {
  emitPersonalChatFanoutToSockets,
  emitRaidOverlayFanoutToTeammateSockets,
  filterPersonalChatFanoutUserIds,
} from './chat-realtime-broadcast.util';

describe('chat-realtime-broadcast.util per-socket fanout', () => {
  const emit = jest.fn();
  const to = jest.fn().mockReturnValue({ emit });
  const server = { to };

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('emitPersonalChatFanoutToSockets targets sockets not in chat room', () => {
    const adapterRooms = new Map<string, Set<string>>([
      ['chat:raid-room', new Set(['socket-in-room'])],
      ['user:t1', new Set(['socket-in-room', 'socket-overlay'])],
      ['user:t2', new Set(['socket-only'])],
    ]);
    const targets = emitPersonalChatFanoutToSockets(
      server,
      adapterRooms,
      ['t1', 't2'],
      'raid-room',
      'message:new',
      { _id: 'm1' },
      'sender',
    );
    expect(targets).toEqual(new Set(['t1', 't2']));
    expect(to).toHaveBeenCalledWith('socket-overlay');
    expect(to).toHaveBeenCalledWith('socket-only');
    expect(to).not.toHaveBeenCalledWith('socket-in-room');
  });

  it('filterPersonalChatFanoutUserIds skips user with any in-room socket', () => {
    const out = filterPersonalChatFanoutUserIds(
      ['t1', 't2'],
      new Set(['t1']),
      'sender',
    );
    expect(out).toEqual(['t2']);
  });

  it('emitRaidOverlayFanoutToTeammateSockets skips personal targets', () => {
    const adapterRooms = new Map<string, Set<string>>([
      ['chat:raid-room', new Set()],
      ['user:t2', new Set(['socket-t2'])],
    ]);
    const count = emitRaidOverlayFanoutToTeammateSockets(
      server,
      adapterRooms,
      ['t2'],
      'raid-room',
      { _id: 'm1' },
      'sender',
      new Set(['t2']),
    );
    expect(count).toBe(0);
    expect(emit).not.toHaveBeenCalled();
  });
});
