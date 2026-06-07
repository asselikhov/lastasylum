import {
  getGameEventById,
  isGameEventNotifyMessageText,
  resolveGameEventId,
} from './game-event-catalog';
import { formatGameEventPushSenderLine } from './game-event-push.util';

describe('game-event-catalog', () => {
  it('resolveGameEventId maps legacy excavationAlert', () => {
    expect(resolveGameEventId(undefined, true)).toBe('hq_excavation');
    expect(resolveGameEventId('pvp_war_started', false)).toBe('pvp_war_started');
    expect(resolveGameEventId('excavation', false)).toBe('hq_excavation');
  });

  it('isGameEventNotifyMessageText matches catalog strip text', () => {
    expect(isGameEventNotifyMessageText('[ШТАБ] Раскопки альянса')).toBe(true);
    expect(isGameEventNotifyMessageText('hello')).toBe(false);
  });

  it('getGameEventById returns definition', () => {
    const e = getGameEventById('pve_gather_5m');
    expect(e?.messageText).toBe('[PvE] Сбор (5 минут)');
    expect(e?.category).toBe('pve');
  });

  it('formatGameEventPushSenderLine matches chat order', () => {
    expect(
      formatGameEventPushSenderLine({
        username: 'Nick',
        teamTag: 'ABC',
        serverNumber: 109,
      }),
    ).toBe('#109 [ABC] Nick');
  });
});
