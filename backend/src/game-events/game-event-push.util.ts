import { ALL_GAME_EVENT_IDS } from './game-event-catalog';

export function userAcceptsGameEventPush(
  user: {
    excavationPushEnabled?: boolean;
    gameEventPushEnabled?: Record<string, boolean> | null;
  },
  eventId: string,
): boolean {
  const map = user.gameEventPushEnabled ?? {};
  if (map[eventId] === false) {
    return false;
  }
  if (eventId === 'hq_excavation' && user.excavationPushEnabled === false) {
    return false;
  }
  return true;
}

/** Full map for API: true unless explicitly disabled in DB or legacy excavation flag. */
export function buildGameEventPushEnabledMap(user: {
  excavationPushEnabled?: boolean;
  gameEventPushEnabled?: Record<string, boolean> | null;
}): Record<string, boolean> {
  const raw = user.gameEventPushEnabled ?? {};
  const out: Record<string, boolean> = {};
  for (const id of ALL_GAME_EVENT_IDS) {
    out[id] = userAcceptsGameEventPush(user, id);
  }
  return out;
}

/** Same order as Android [chatSenderDisplayLine]: `#109 [TAG] nickname`. */
export function formatGameEventPushSenderLine(input: {
  username: string;
  teamTag?: string | null;
  serverNumber?: number | null;
}): string {
  const u = (input.username ?? '').trim() || '—';
  const parts: string[] = [];
  const server = input.serverNumber;
  if (typeof server === 'number' && server >= 1) {
    parts.push(`#${server}`);
  }
  const rawTag = (input.teamTag ?? '').trim().replace(/^\[|\]$/g, '');
  if (rawTag.length > 0) {
    parts.push(`[${rawTag}]`);
  }
  return parts.length > 0 ? `${parts.join(' ')} ${u}` : u;
}
