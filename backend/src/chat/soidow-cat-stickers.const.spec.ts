import {
  isSoidowCatStickerOnlyMessage,
  parseSoidowCatStickerStem,
} from './soidow-cat-stickers.const';
import { assertStickerPayload } from './sticker-payload.util';

describe('soidow-cat-stickers.const', () => {
  it('parses wire message', () => {
    expect(parseSoidowCatStickerStem('[[soidow_cat:00ef3379]]')).toBe(
      '00ef3379',
    );
    expect(isSoidowCatStickerOnlyMessage('[[soidow_cat:00ef3379]]')).toBe(true);
  });

  it('rejects unknown stem via assertStickerPayload', () => {
    expect(() => assertStickerPayload('[[soidow_cat:unknown-stem]]')).toThrow(
      'Unknown sticker',
    );
  });
});
