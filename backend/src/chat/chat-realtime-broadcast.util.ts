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
