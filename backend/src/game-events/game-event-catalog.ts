export type GameEventCategory = 'hq' | 'pve' | 'pvp';

export interface GameEventDefinition {
  id: string;
  category: GameEventCategory;
  /** Exact raid chat / overlay strip text */
  messageText: string;
  channelId: string;
}

export const GAME_EVENT_CATALOG: readonly GameEventDefinition[] = [
  {
    id: 'hq_excavation',
    category: 'hq',
    messageText: '[ШТАБ] Раскопки альянса',
    channelId: 'game_event_hq_excavation',
  },
  {
    id: 'hq_enemy_at_gates',
    category: 'hq',
    messageText: '[ШТАБ] Враг у ворот',
    channelId: 'game_event_hq_enemy_at_gates',
  },
  {
    id: 'hq_all_online',
    category: 'hq',
    messageText: '[ШТАБ] Всем онлайн',
    channelId: 'game_event_hq_all_online',
  },
  {
    id: 'hq_important',
    category: 'hq',
    messageText: '[ШТАБ] Важное объявление',
    channelId: 'game_event_hq_important',
  },
  {
    id: 'hq_help_needed',
    category: 'hq',
    messageText: '[ШТАБ] Требуется помощь альянсу',
    channelId: 'game_event_hq_help_needed',
  },
  {
    id: 'pve_gather_5m',
    category: 'pve',
    messageText: '[PvE] Сбор (5 минут)',
    channelId: 'game_event_pve_gather_5m',
  },
  {
    id: 'pve_event_started',
    category: 'pve',
    messageText: '[PvE] Событие началось',
    channelId: 'game_event_pve_event_started',
  },
  {
    id: 'pvp_gather_5m',
    category: 'pvp',
    messageText: '[PvP] Сбор (5 минут)',
    channelId: 'game_event_pvp_gather_5m',
  },
  {
    id: 'pvp_war_started',
    category: 'pvp',
    messageText: '[PvP] Война началась',
    channelId: 'game_event_pvp_war_started',
  },
] as const;

const BY_ID = new Map(GAME_EVENT_CATALOG.map((e) => [e.id, e]));
const BY_MESSAGE_TEXT = new Map(
  GAME_EVENT_CATALOG.map((e) => [e.messageText, e]),
);

export function resolveGameEventId(
  gameEventAlert?: string | null,
  excavationAlert?: boolean,
): string | null {
  const raw = gameEventAlert?.trim();
  if (raw) {
    if (raw === 'excavation') {
      return 'hq_excavation';
    }
    return BY_ID.has(raw) ? raw : null;
  }
  if (excavationAlert === true) {
    return 'hq_excavation';
  }
  return null;
}

export function getGameEventById(eventId: string): GameEventDefinition | null {
  return BY_ID.get(eventId.trim()) ?? null;
}

export function isValidGameEventId(eventId: string): boolean {
  return BY_ID.has(eventId.trim());
}

/** Raid chat / overlay strip text for catalog game-event notifies. */
export function isGameEventNotifyMessageText(text: string): boolean {
  return BY_MESSAGE_TEXT.has(text.trim());
}

export const ALL_GAME_EVENT_IDS = GAME_EVENT_CATALOG.map((e) => e.id);
