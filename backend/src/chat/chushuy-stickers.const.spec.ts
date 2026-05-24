import {
  isChushuyStickerOnlyMessage,
  parseChushuyStickerStem,
} from './chushuy-stickers.const';
import { assertStickerPayload } from './sticker-payload.util';

describe('chushuy-stickers.const', () => {
  it('parses wire message', () => {
    expect(parseChushuyStickerStem('[[chushuy:0b305b8a]]')).toBe('0b305b8a');
    expect(isChushuyStickerOnlyMessage('[[chushuy:0b305b8a]]')).toBe(true);
  });

  it('rejects unknown stem via assertStickerPayload', () => {
    expect(() => assertStickerPayload('[[chushuy:unknown-stem]]')).toThrow(
      'Unknown sticker',
    );
  });
});
