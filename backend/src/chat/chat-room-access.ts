import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/chat-room-constants';
import type { User } from '../users/schemas/user.schema';
import {
  isServerChatScope,
  parseServerNumberFromChatScope,
  playerTeamChatAllianceId,
} from './chat-alliance-scope';
import { resolveUserActiveServerNumber } from './chat-user-server';

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
 * «Межсерв» и комната сервера — для всех; «Альянс» и «Рейд» — только участникам player team.
 */
export function userMayAccessChatRoom(
  user: Pick<
    User,
    'allianceName' | 'playerTeamId' | 'gameIdentities' | 'activeGameIdentityId'
  >,
  room: ChatRoomAccessFields,
): boolean {
  if (room.archivedAt) {
    return false;
  }
  if (room.allianceId === GLOBAL_CHAT_ALLIANCE_ID) {
    return true;
  }
  if (isServerChatScope(room.allianceId)) {
    const roomServer = parseServerNumberFromChatScope(room.allianceId);
    const userServer = resolveUserActiveServerNumber(user);
    return (
      roomServer != null && userServer != null && roomServer === userServer
    );
  }
  const teamScope = resolveTeamChatScope(user);
  if (!teamScope) {
    return false;
  }
  return room.allianceId === teamScope;
}
