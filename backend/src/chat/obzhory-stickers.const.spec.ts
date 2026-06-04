import {
  isObzhoryStickerOnlyMessage,
  parseObzhoryStickerStem,
} from './obzhory-stickers.const';
import { assertStickerPayload } from './sticker-payload.util';

describe('obzhory-stickers.const', () => {
  it('parses obzhory wire message', () => {
    expect(parseObzhoryStickerStem('[[obzhory:2]]')).toBe('2');
    expect(isObzhoryStickerOnlyMessage('[[obzhory:2]]')).toBe(true);
  });

  it('rejects unknown stem via assertStickerPayload', () => {
    expect(() => assertStickerPayload('[[obzhory:unknown-stem]]')).toThrow(
      'Unknown sticker',
    );
  });
});
