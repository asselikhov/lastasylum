import {
  getGameEventById,
  resolveGameEventId,
} from './game-event-catalog';

describe('game-event-catalog', () => {
  it('resolveGameEventId maps legacy excavationAlert', () => {
    expect(resolveGameEventId(undefined, true)).toBe('hq_excavation');
    expect(resolveGameEventId('pvp_war_started', false)).toBe('pvp_war_started');
  });

  it('getGameEventById returns definition', () => {
    const e = getGameEventById('pve_gather_5m');
    expect(e?.messageText).toBe('[PvE] Сбор (5 минут)');
    expect(e?.category).toBe('pve');
  });
});
