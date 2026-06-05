import { Types } from 'mongoose';

export const PIN_HISTORY_MAX = 15;

export type PinHistoryEntry = {
  messageId: Types.ObjectId;
  pinnedAt: Date;
  pinnedByUserId: string;
};

export type PinHistoryCarrier = {
  pinnedMessageId?: Types.ObjectId | null;
  pinnedAt?: Date | null;
  pinnedByUserId?: string | null;
  pinHistory?: PinHistoryEntry[];
};

export function normalizePinHistory(
  raw: PinHistoryEntry[] | null | undefined,
): PinHistoryEntry[] {
  if (!Array.isArray(raw)) return [];
  return raw
    .filter(
      (e) =>
        e?.messageId &&
        Types.ObjectId.isValid(String(e.messageId)) &&
        e.pinnedAt instanceof Date &&
        typeof e.pinnedByUserId === 'string' &&
        e.pinnedByUserId.trim().length > 0,
    )
    .slice(0, PIN_HISTORY_MAX);
}

/** Fill pinHistory from legacy single-pin fields when missing. */
export function ensurePinHistoryMigrated(
  carrier: PinHistoryCarrier,
): PinHistoryEntry[] {
  const existing = normalizePinHistory(carrier.pinHistory);
  if (existing.length > 0) return existing;
  const pinId = carrier.pinnedMessageId;
  if (!pinId || !Types.ObjectId.isValid(String(pinId))) return [];
  return [
    {
      messageId: new Types.ObjectId(String(pinId)),
      pinnedAt: carrier.pinnedAt ?? new Date(0),
      pinnedByUserId: carrier.pinnedByUserId?.trim() || '',
    },
  ].filter((e) => e.pinnedByUserId.length > 0);
}

export function pushPinHistoryEntry(
  history: PinHistoryEntry[],
  entry: PinHistoryEntry,
): PinHistoryEntry[] {
  const id = entry.messageId.toString();
  const without = history.filter((h) => h.messageId.toString() !== id);
  return [entry, ...without].slice(0, PIN_HISTORY_MAX);
}

export function removePinHistoryEntry(
  history: PinHistoryEntry[],
  messageId: string,
): PinHistoryEntry[] {
  const id = messageId.trim();
  if (!id) return history;
  return history.filter((h) => h.messageId.toString() !== id);
}

export type ActivePinState = {
  pinnedMessageId: Types.ObjectId | null;
  pinnedAt: Date | null;
  pinnedByUserId: string | null;
};

export function activePinFromHistory(history: PinHistoryEntry[]): ActivePinState {
  const head = history[0];
  if (!head) {
    return {
      pinnedMessageId: null,
      pinnedAt: null,
      pinnedByUserId: null,
    };
  }
  return {
    pinnedMessageId: head.messageId,
    pinnedAt: head.pinnedAt,
    pinnedByUserId: head.pinnedByUserId,
  };
}

export function clearAllPinHistory(): {
  pinHistory: PinHistoryEntry[];
  pinnedMessageId: null;
  pinnedAt: null;
  pinnedByUserId: null;
} {
  return {
    pinHistory: [],
    pinnedMessageId: null,
    pinnedAt: null,
    pinnedByUserId: null,
  };
}
