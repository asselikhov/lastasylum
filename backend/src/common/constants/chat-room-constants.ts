/**
 * Cross-server lobby: all players, any server and team membership.
 */
export const GLOBAL_CHAT_ALLIANCE_ID = '__global__';

export const GLOBAL_CHAT_ROOM_TITLE = 'Межсерв';

/** Per-server public lobby (`srv:<n>`). */
export const SERVER_CHAT_ALLIANCE_PREFIX = 'srv:';

/** Player-team hub room title (sortOrder 1 under `pt:<teamId>`). */
export const ALLIANCE_HUB_ROOM_TITLE = 'Альянс';

export const ALLIANCE_RAID_ROOM_TITLE = 'Рейд';

export function serverChatAllianceId(serverNumber: number): string {
  return `${SERVER_CHAT_ALLIANCE_PREFIX}${serverNumber}`;
}

export function formatServerChatRoomTitle(serverNumber: number): string {
  return `#${serverNumber}`;
}

export function isServerChatScope(allianceId: string): boolean {
  return allianceId.startsWith(SERVER_CHAT_ALLIANCE_PREFIX);
}

export function parseServerNumberFromChatScope(
  allianceId: string,
): number | null {
  if (!isServerChatScope(allianceId)) return null;
  const n = Number.parseInt(allianceId.slice(SERVER_CHAT_ALLIANCE_PREFIX.length), 10);
  return Number.isFinite(n) && n >= 1 ? n : null;
}
