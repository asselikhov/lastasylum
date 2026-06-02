export type OverlayPersonalReactionSendMode = 'regular' | 'reply';

export type OverlayPersonalDeliveryEvent =
  | 'overlay:reaction'
  | 'overlay:reaction:reply';

export function parseOverlayReplyToLogId(raw: unknown): string {
  return typeof raw === 'string' ? raw.trim() : '';
}

/** Resolves persisted replyToLogId or throws a message for WsException. */
export function resolveOverlayPersonalReplyToLogId(
  mode: OverlayPersonalReactionSendMode,
  rawReplyToLogId: unknown,
): string | null {
  const replyToLogId = parseOverlayReplyToLogId(rawReplyToLogId);
  if (mode === 'regular' && replyToLogId) {
    throw new Error(
      'replyToLogId is not allowed on overlay:reaction; use overlay:reaction:reply',
    );
  }
  if (mode === 'reply' && !replyToLogId) {
    throw new Error('replyToLogId is required');
  }
  return mode === 'reply' ? replyToLogId : null;
}

export function overlayPersonalDeliveryEvent(
  mode: OverlayPersonalReactionSendMode,
): OverlayPersonalDeliveryEvent {
  return mode === 'reply' ? 'overlay:reaction:reply' : 'overlay:reaction';
}

export function overlayPersonalAckEvent(
  mode: OverlayPersonalReactionSendMode,
): string {
  return mode === 'reply'
    ? 'overlay:reaction:reply:sent'
    : 'overlay:reaction:sent';
}
