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
