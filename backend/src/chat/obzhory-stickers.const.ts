/**
 * Stems shipped in the Android app under assets/stickerpacks/obzhory/*.png
 * (must stay in sync with the client pack).
 */
export const OBZHORY_STICKER_STEMS: ReadonlySet<string> = new Set(['1', '2', '3']);

const OBZHORY_WIRE = /^\[\[obzhory:([^[\]]+)\]\]$/;

export function parseObzhoryStickerStem(text: string): string | null {
  const m = text.trim().match(OBZHORY_WIRE);
  return m ? m[1] : null;
}

export function isObzhoryStickerOnlyMessage(text: string): boolean {
  return parseObzhoryStickerStem(text) != null;
}
