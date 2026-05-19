import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/global-chat-alliance-id';
import type { User } from '../users/schemas/user.schema';
import { playerTeamChatAllianceId } from './chat-alliance-scope';

export type ChatRoomAccessFields = {
  allianceId: string;
  archivedAt?: Date | null;
};

/** Per-team chat scope (`pt:<teamId>`), or null when the user has no player team. */
export function resolveTeamChatScope(
  user: Pick<User, 'playerTeamId'>,
): string | null {
  if (!user.playerTeamId) {
    return null;
  }
  return playerTeamChatAllianceId(user.playerTeamId.toString());
}

/**
 * «Мир» — для всех; комната команды и «Рейд» — только участникам этой player team.
 */
export function userMayAccessChatRoom(
  user: Pick<User, 'allianceName' | 'playerTeamId'>,
  room: ChatRoomAccessFields,
): boolean {
  if (room.archivedAt) {
    return false;
  }
  if (room.allianceId === GLOBAL_CHAT_ALLIANCE_ID) {
    return true;
  }
  const teamScope = resolveTeamChatScope(user);
  if (!teamScope) {
    return false;
  }
  return room.allianceId === teamScope;
}
