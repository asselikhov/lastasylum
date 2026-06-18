/** Neutral socket payload for `message:reaction` (no per-viewer reactedByMe). */
export type MessageReactionBroadcastPayload = {
  messageId: string;
  roomId: string;
  reactions: { emoji: string; count: number; userIds: string[] }[];
};

export function buildMessageReactionBroadcastPayload(input: {
  messageId: string;
  roomId: string;
  reactions?: { emoji: string; userIds: string[] }[] | null;
}): MessageReactionBroadcastPayload {
  const reactions = (input.reactions ?? []).map((r) => ({
    emoji: r.emoji,
    count: r.userIds.length,
    userIds: r.userIds,
  }));
  return {
    messageId: input.messageId,
    roomId: input.roomId,
    reactions,
  };
}

/** Personal `user:{id}` fanout — skip users already in `chat:{roomId}` socket room. */
export function filterPersonalChatFanoutUserIds(
  eligibleUserIds: string[],
  inRoomUserIds: Set<string>,
  excludeUserId?: string,
): string[] {
  const exclude = excludeUserId?.trim();
  const out: string[] = [];
  for (const raw of eligibleUserIds) {
    const userId = raw.trim();
    if (!userId) continue;
    if (exclude && userId === exclude) continue;
    if (inRoomUserIds.has(userId)) continue;
    out.push(userId);
  }
  return out;
}

type SocketEmitServer = {
  to: (target: string) => { emit: (event: string, payload: unknown) => void };
};

/**
 * Emit to eligible users' sockets that are not joined to `chat:{roomId}`.
 * Per-socket fanout so overlay-only sockets still receive traffic when another
 * socket for the same user is already in the room.
 */
export function emitPersonalChatFanoutToSockets(
  server: SocketEmitServer | undefined,
  adapterRooms: Map<string, Set<string>> | undefined,
  eligibleUserIds: string[],
  roomId: string,
  event: string,
  payload: unknown,
  excludeUserId?: string,
): Set<string> {
  const rid = roomId.trim();
  const exclude = excludeUserId?.trim();
  const inRoomSocketIds = adapterRooms?.get(`chat:${rid}`) ?? new Set<string>();
  const personalTargetUserIds = new Set<string>();
  for (const raw of eligibleUserIds) {
    const userId = raw.trim();
    if (!userId) continue;
    if (exclude && userId === exclude) continue;
    const userSockets = adapterRooms?.get(`user:${userId}`);
    if (!userSockets) continue;
    let emitted = false;
    for (const socketId of userSockets) {
      if (inRoomSocketIds.has(socketId)) continue;
      server?.to(socketId).emit(event, payload);
      emitted = true;
    }
    if (emitted) personalTargetUserIds.add(userId);
  }
  return personalTargetUserIds;
}

/** Emit `message:new` to ingame teammate sockets not in `chat:{roomId}`. */
export function emitRaidOverlayFanoutToTeammateSockets(
  server: SocketEmitServer | undefined,
  adapterRooms: Map<string, Set<string>> | undefined,
  teammateUserIds: string[],
  roomId: string,
  payload: unknown,
  excludeUserId: string,
  skipUserIds: Set<string>,
): number {
  const rid = roomId.trim();
  const exclude = excludeUserId.trim();
  const inRoomSocketIds = adapterRooms?.get(`chat:${rid}`) ?? new Set<string>();
  let fanoutCount = 0;
  for (const raw of teammateUserIds) {
    const teammateId = raw.trim();
    if (!teammateId || teammateId === exclude) continue;
    if (skipUserIds.has(teammateId)) continue;
    const userSockets = adapterRooms?.get(`user:${teammateId}`);
    if (!userSockets) continue;
    let emitted = false;
    for (const socketId of userSockets) {
      if (inRoomSocketIds.has(socketId)) continue;
      server?.to(socketId).emit('message:new', payload);
      emitted = true;
    }
    if (emitted) fanoutCount++;
  }
  return fanoutCount;
}
