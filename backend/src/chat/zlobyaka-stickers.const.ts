/**
 * Stems shipped in the Android app under assets/stickerpacks/zlobyaka/*.png
 * (must stay in sync with the client pack).
 */
export const ZLOBYAKA_STICKER_STEMS: ReadonlySet<string> = new Set(
  Array.from({ length: 96679 - 96632 + 1 }, (_, i) => `1-${96632 + i}-512b`),
);

const ZLOBYAKA_WIRE = /^\[\[zlobyaka:([^[\]]+)\]\]$/;

export function parseZlobyakaStickerStem(text: string): string | null {
  const m = text.trim().match(ZLOBYAKA_WIRE);
  return m ? m[1] : null;
}

export function isZlobyakaStickerOnlyMessage(text: string): boolean {
  return parseZlobyakaStickerStem(text) != null;
}

/** Short body for FCM / logs (Russian, same meaning as the Android string). */
export function formatChatPushBody(text: string): string {
  if (isZlobyakaStickerOnlyMessage(text)) {
    return 'Стикер «Злобяка»';
  }
  const t = text.trim();
  return t.length > 140 ? `${t.slice(0, 137)}...` : t;
}
