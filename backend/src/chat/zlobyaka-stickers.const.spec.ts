import {
  formatChatPushBody,
  isZlobyakaStickerOnlyMessage,
  parseZlobyakaStickerStem,
  ZLOBYAKA_STICKER_STEMS,
} from './zlobyaka-stickers.const';

describe('zlobyaka-stickers.const', () => {
  it('has 48 stems', () => {
    expect(ZLOBYAKA_STICKER_STEMS.size).toBe(48);
  });

  it('parses valid wire payload', () => {
    expect(parseZlobyakaStickerStem('[[zlobyaka:1-96632-512b]]')).toBe(
      '1-96632-512b',
    );
    expect(isZlobyakaStickerOnlyMessage('[[zlobyaka:1-96632-512b]]')).toBe(
      true,
    );
  });

  it('rejects non-exact payloads', () => {
    expect(parseZlobyakaStickerStem('prefix [[zlobyaka:1-96632-512b]]')).toBe(
      null,
    );
    expect(parseZlobyakaStickerStem('[[zlobyaka:1-96632-512b]] trailing')).toBe(
      null,
    );
  });

  it('formatChatPushBody maps sticker to short label', () => {
    expect(formatChatPushBody('[[zlobyaka:1-96632-512b]]')).toBe(
      'Стикер «Злобяка»',
    );
  });
});
