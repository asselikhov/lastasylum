import { isZlobyakaStickerOnlyMessage } from './zlobyaka-stickers.const';
import { STICKER_PACK_ZLOBYAKA } from '../common/constants/sticker-packs';

/**
 * If the body is exactly one supported sticker wire message, returns its pack key.
 */
export function stickerPackKeyFromStickerOnlyMessage(text: string): string | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  if (isZlobyakaStickerOnlyMessage(trimmed)) return STICKER_PACK_ZLOBYAKA;
  return null;
}
