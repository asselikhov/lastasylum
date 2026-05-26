import {
  isKnownStickerPackKey,
  STICKER_PACK_ZLOBYAKA,
} from '../common/constants/sticker-packs';
import { isZlobyakaStickerOnlyMessage } from './zlobyaka-stickers.const';

const GENERIC_STICKER_WIRE = /^\[\[([a-z][a-z0-9_]*):([^[\]]+)\]\]$/;

export type ParsedStickerWire = {
  packKey: string;
  stem: string;
};

/**
 * If the body is exactly one supported sticker wire message, returns pack key + stem.
 */
export function parseStickerOnlyMessage(
  text: string,
): ParsedStickerWire | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  const m = trimmed.match(GENERIC_STICKER_WIRE);
  if (m) {
    const packKey = m[1];
    const stem = m[2]?.trim() ?? '';
    if (!stem || !isKnownStickerPackKey(packKey)) return null;
    return { packKey, stem };
  }
  if (isZlobyakaStickerOnlyMessage(trimmed)) {
    const stem = trimmed.replace(/^\[\[zlobyaka:/, '').replace(/\]\]$/, '');
    return { packKey: STICKER_PACK_ZLOBYAKA, stem };
  }
  return null;
}

/**
 * If the body is exactly one supported sticker wire message, returns its pack key.
 */
export function stickerPackKeyFromStickerOnlyMessage(
  text: string,
): string | null {
  return parseStickerOnlyMessage(text)?.packKey ?? null;
}
