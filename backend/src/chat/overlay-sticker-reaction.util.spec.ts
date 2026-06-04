import {
  encodeOverlayChatStickerReactionId,
  normalizeOverlayChatStickerReaction,
  parseOverlayChatStickerReactionId,
} from './overlay-sticker-reaction.util';

describe('overlay-sticker-reaction.util', () => {
  it('parses and normalizes chushuy overlay sticker id', () => {
    const id = encodeOverlayChatStickerReactionId('chushuy', '0b305b8a');
    expect(id).toBe('sticker/chushuy/0b305b8a');
    expect(parseOverlayChatStickerReactionId(id)).toEqual({
      packKey: 'chushuy',
      stem: '0b305b8a',
    });
    expect(normalizeOverlayChatStickerReaction(id)).toBe(id);
  });

  it('parses zlobyaka stem with hyphens', () => {
    const id = 'sticker/zlobyaka/1-96632-512b';
    expect(parseOverlayChatStickerReactionId(id)).toEqual({
      packKey: 'zlobyaka',
      stem: '1-96632-512b',
    });
  });

  it('parses and normalizes obzhory overlay sticker id', () => {
    const id = encodeOverlayChatStickerReactionId('obzhory', '2');
    expect(id).toBe('sticker/obzhory/2');
    expect(parseOverlayChatStickerReactionId(id)).toEqual({
      packKey: 'obzhory',
      stem: '2',
    });
    expect(normalizeOverlayChatStickerReaction(id)).toBe(id);
  });

  it('rejects misc overlay ids (sticker_01)', () => {
    expect(parseOverlayChatStickerReactionId('sticker_01')).toBeNull();
    expect(normalizeOverlayChatStickerReaction('sticker_01')).toBeNull();
  });

  it('rejects unknown pack or stem', () => {
    expect(parseOverlayChatStickerReactionId('sticker/unknown/x')).toBeNull();
    expect(parseOverlayChatStickerReactionId('sticker/chushuy/unknown-stem')).toBeNull();
  });
});
