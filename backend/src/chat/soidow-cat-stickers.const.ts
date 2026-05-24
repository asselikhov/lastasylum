/**
 * Stems shipped in the Android app under assets/stickerpacks/soidow_cat/*.png
 * (must stay in sync with the client pack).
 */
export const SOIDOW_CAT_STICKER_STEMS: ReadonlySet<string> = new Set([
  '00ef3379',
  '13c9bc53',
  '1eb7b514',
  '244b7070',
  '2926dbe7',
  '2bed651b',
  '2cc57806',
  '2dd46045',
  '345b2693',
  '3657f13c',
  '3e4bf651',
  '43429d89',
  '49afa8d0',
  '509e3605',
  '52420495',
  '537893b3',
  '5774c377',
  '59a3e53a',
  '5cf4f259',
  '6ad22801',
  '6d33efa7',
  '6da958de',
  '71031c19',
  '71d0abd1',
  '71e44253',
  '73691bfc',
  '77a94be1',
  '7a246376',
  '7d3722f1',
  '7f326dec',
  '885f93aa',
  '8e5581bf',
  '942cf0b3',
  '9a72e669',
  '9f04022b',
  'a49dc763',
  'a95c4cef',
  'b87aaf62',
  'bb3594ff',
  'c300933f',
  'c91ad1a3',
  'cb9d9b42',
  'ce5912a3',
  'd1f6a482',
  'eacd8543',
  'f0973888',
  'f7fcace5',
  'f8955c08',
  'fa2e8c65',
  'fdbd2cc6',
]);

const SOIDOW_CAT_WIRE = /^\[\[soidow_cat:([^[\]]+)\]\]$/;

export function parseSoidowCatStickerStem(text: string): string | null {
  const m = text.trim().match(SOIDOW_CAT_WIRE);
  return m ? m[1] : null;
}

export function isSoidowCatStickerOnlyMessage(text: string): boolean {
  return parseSoidowCatStickerStem(text) != null;
}
