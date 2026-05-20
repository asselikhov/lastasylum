import { Types } from 'mongoose';
import type { User } from '../users/schemas/user.schema';

/** Chat rooms for an in-app player team use `pt:<mongoTeamId>` as [ChatRoom.allianceId]. */
export const PLAYER_TEAM_CHAT_PREFIX = 'pt:';

export {
  isServerChatScope,
  parseServerNumberFromChatScope,
  serverChatAllianceId,
  SERVER_CHAT_ALLIANCE_PREFIX,
} from '../common/constants/chat-room-constants';

export function playerTeamChatAllianceId(teamId: string): string {
  return `${PLAYER_TEAM_CHAT_PREFIX}${teamId}`;
}

export function isPlayerTeamChatScope(scope: string): boolean {
  return scope.startsWith(PLAYER_TEAM_CHAT_PREFIX);
}

export function parsePlayerTeamIdFromChatScope(scope: string): string | null {
  if (!isPlayerTeamChatScope(scope)) return null;
  const id = scope.slice(PLAYER_TEAM_CHAT_PREFIX.length).trim();
  return Types.ObjectId.isValid(id) ? id : null;
}

/**
 * Alliance-scoped hub/raid rooms for users without a player team; otherwise per-team rooms.
 */
export function resolveChatAllianceScope(
  user: Pick<User, 'allianceName' | 'playerTeamId'>,
): string {
  if (user.playerTeamId) {
    return playerTeamChatAllianceId(user.playerTeamId.toString());
  }
  return user.allianceName;
}
