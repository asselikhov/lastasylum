/**
 * Stems shipped in the Android app under assets/stickerpacks/chushuy/*.png
 * (must stay in sync with the client pack).
 */
export const CHUSHUY_STICKER_STEMS: ReadonlySet<string> = new Set([
  '0b305b8a',
  '0be53857',
  '0de346e7',
  '0f80f742',
  '199f2901',
  '1c6f1024',
  '1de31548',
  '20c8ac16',
  '20f8484c',
  '2477815e',
  '29bb2439',
  '2e539d85',
  '31194131',
  '38dc6601',
  '40f367d6',
  '4116c662',
  '45ef35e8',
  '50eafa1e',
  '52d98ef5',
  '58156e40',
  '633a7769',
  '6ab17dd5',
  '6cfa2d73',
  '70dc2940',
  '7854ace7',
  '7c95e0c7',
  '80a49f87',
  '81e7fb89',
  '828b0cee',
  '82f91031',
  '835f85d4',
  '8e58d60b',
  '9616dc27',
  '97ed38f9',
  '9ad7163e',
  'a9f74f8b',
  'ab71e72d',
  'ac43e8cd',
  'ac617d32',
  'b281117e',
  'cd1df3c1',
  'cfa74b60',
  'd2e14475',
  'd41ff953',
  'f6f3f7b5',
  'fb6b79bd',
  'fbcc29fe',
]);

const CHUSHUY_WIRE = /^\[\[chushuy:([^[\]]+)\]\]$/;

export function parseChushuyStickerStem(text: string): string | null {
  const m = text.trim().match(CHUSHUY_WIRE);
  return m ? m[1] : null;
}

export function isChushuyStickerOnlyMessage(text: string): boolean {
  return parseChushuyStickerStem(text) != null;
}
