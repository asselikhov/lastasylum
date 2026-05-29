import { isKnownStickerPackKey } from '../common/constants/sticker-packs';
import { isKnownStickerStem } from './sticker-payload.util';

/** Must match Android OverlayQuickReactions.kt CHAT_STICKER_REACTION_ID_PREFIX. */
export const OVERLAY_CHAT_STICKER_REACTION_PREFIX = 'sticker/';

const MAX_OVERLAY_STICKER_REACTION_LEN = 128;

export type ParsedOverlayChatStickerReaction = {
  packKey: string;
  stem: string;
};

export function encodeOverlayChatStickerReactionId(
  packKey: string,
  stem: string,
): string {
  return `${OVERLAY_CHAT_STICKER_REACTION_PREFIX}${packKey}/${stem}`;
}

export function parseOverlayChatStickerReactionId(
  reactionId: string,
): ParsedOverlayChatStickerReaction | null {
  const trimmed = reactionId.trim();
  if (!trimmed.startsWith(OVERLAY_CHAT_STICKER_REACTION_PREFIX)) {
    return null;
  }
  if (trimmed.length > MAX_OVERLAY_STICKER_REACTION_LEN) return null;
  const rest = trimmed.slice(OVERLAY_CHAT_STICKER_REACTION_PREFIX.length);
  const slash = rest.indexOf('/');
  if (slash <= 0 || slash >= rest.length - 1) return null;
  const packKey = rest.slice(0, slash).trim();
  const stem = rest.slice(slash + 1).trim();
  if (!packKey || !stem) return null;
  if (!isKnownStickerPackKey(packKey)) return null;
  if (!isKnownStickerStem(packKey, stem)) return null;
  return { packKey, stem };
}

/** Canonical overlay reaction id for a built-in chat sticker, or null if invalid. */
export function normalizeOverlayChatStickerReaction(
  raw: string,
): string | null {
  const parsed = parseOverlayChatStickerReactionId(raw);
  if (!parsed) return null;
  return encodeOverlayChatStickerReactionId(parsed.packKey, parsed.stem);
}
