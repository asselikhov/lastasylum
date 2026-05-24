import {
  parseStickerOnlyMessage,
  stickerPackKeyFromStickerOnlyMessage,
} from './sticker-wire.util';

describe('sticker-wire.util', () => {
  it('parses chushuy wire message', () => {
    const parsed = parseStickerOnlyMessage('[[chushuy:0b305b8a]]');
    expect(parsed).toEqual({ packKey: 'chushuy', stem: '0b305b8a' });
  });

  it('parses zlobyaka wire message', () => {
    const parsed = parseStickerOnlyMessage('[[zlobyaka:1-96632-512b]]');
    expect(parsed).toEqual({ packKey: 'zlobyaka', stem: '1-96632-512b' });
    expect(stickerPackKeyFromStickerOnlyMessage('[[zlobyaka:1-96632-512b]]')).toBe(
      'zlobyaka',
    );
  });

  it('rejects unknown pack keys', () => {
    expect(parseStickerOnlyMessage('[[unknown:stem]]')).toBeNull();
  });

  it('rejects non-sticker text', () => {
    expect(parseStickerOnlyMessage('hello')).toBeNull();
  });
});
