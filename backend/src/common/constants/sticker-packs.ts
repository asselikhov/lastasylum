/**
 * Built-in sticker pack keys (wire prefix must match client packs).
 *
 * Adding a pack:
 * 1. `assets/stickerpacks/<key>/*.png` in Android APK.
 * 2. `*StickerPack` class + register in `StickerPacks.ALL_PACKS`.
 * 3. Append key here + stems const on backend + title in `StickerAccessService.catalog()`.
 * 4. Rebuild app (stickers are not loaded from CDN).
 *
 * Manual QA: admin role/user grants; player sees only granted packs in chat/forum/overlay;
 * send forbidden pack → 403; PATCH user grants must not clear role grants.
 */
export const STICKER_PACK_ZLOBYAKA = 'zlobyaka' as const;
export const STICKER_PACK_CHUSHUY = 'chushuy' as const;

export const KNOWN_STICKER_PACK_KEYS: readonly string[] = [
  STICKER_PACK_ZLOBYAKA,
  STICKER_PACK_CHUSHUY,
];

export function isKnownStickerPackKey(key: string): boolean {
  return KNOWN_STICKER_PACK_KEYS.includes(key);
}
