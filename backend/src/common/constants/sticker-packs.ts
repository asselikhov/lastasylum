/** Built-in sticker pack keys (wire prefix must match client packs). */
export const STICKER_PACK_ZLOBYAKA = 'zlobyaka' as const;

export const KNOWN_STICKER_PACK_KEYS: readonly string[] = [STICKER_PACK_ZLOBYAKA];

export function isKnownStickerPackKey(key: string): boolean {
  return KNOWN_STICKER_PACK_KEYS.includes(key);
}
