import { Types } from 'mongoose';
import {
  activePinFromHistory,
  pushPinHistoryEntry,
  removePinHistoryEntry,
  ensurePinHistoryMigrated,
} from './pin-history.util';

describe('pin-history.util', () => {
  const entry = (id: string) => ({
    messageId: new Types.ObjectId(id),
    pinnedAt: new Date('2026-01-01T00:00:00Z'),
    pinnedByUserId: 'user1',
  });

  it('migrates legacy single pin into history', () => {
    const id = '507f1f77bcf86cd799439014';
    const history = ensurePinHistoryMigrated({
      pinnedMessageId: new Types.ObjectId(id),
      pinnedAt: new Date('2026-01-02T00:00:00Z'),
      pinnedByUserId: 'officer',
      pinHistory: [],
    });
    expect(history).toHaveLength(1);
    expect(history[0].messageId.toString()).toBe(id);
  });

  it('push dedupes and keeps newest first', () => {
    const a = entry('507f1f77bcf86cd799439011');
    const b = entry('507f1f77bcf86cd799439012');
    const pushed = pushPinHistoryEntry([a], b);
    expect(pushed).toHaveLength(2);
    expect(pushed[0].messageId.toString()).toBe(b.messageId.toString());
    const replaced = pushPinHistoryEntry(pushed, {
      ...a,
      pinnedAt: new Date('2026-02-01T00:00:00Z'),
    });
    expect(replaced[0].messageId.toString()).toBe(a.messageId.toString());
    expect(replaced).toHaveLength(2);
  });

  it('remove recalculates active pin', () => {
    const a = entry('507f1f77bcf86cd799439011');
    const b = entry('507f1f77bcf86cd799439012');
    const history = [b, a];
    const next = removePinHistoryEntry(history, b.messageId.toString());
    const active = activePinFromHistory(next);
    expect(active.pinnedMessageId?.toString()).toBe(a.messageId.toString());
  });
});
