import { BadRequestException } from '@nestjs/common';
import {
  STICKER_PACK_CHUSHUY,
  STICKER_PACK_SOIDOW_CAT,
  STICKER_PACK_ZLOBYAKA,
} from '../common/constants/sticker-packs';
import { CHUSHUY_STICKER_STEMS } from './chushuy-stickers.const';
import { parseStickerOnlyMessage } from './sticker-wire.util';
import { SOIDOW_CAT_STICKER_STEMS } from './soidow-cat-stickers.const';
import { ZLOBYAKA_STICKER_STEMS } from './zlobyaka-stickers.const';

const STEM_CATALOGS: Record<string, ReadonlySet<string>> = {
  [STICKER_PACK_ZLOBYAKA]: ZLOBYAKA_STICKER_STEMS,
  [STICKER_PACK_CHUSHUY]: CHUSHUY_STICKER_STEMS,
  [STICKER_PACK_SOIDOW_CAT]: SOIDOW_CAT_STICKER_STEMS,
};

export function isKnownStickerStem(packKey: string, stem: string): boolean {
  const catalog = STEM_CATALOGS[packKey];
  return catalog?.has(stem) ?? false;
}

/** If the body is a sticker wire message, the stem must exist in that pack's catalog. */
export function assertStickerPayload(text: string): void {
  const parsed = parseStickerOnlyMessage(text);
  if (!parsed) return;
  const catalog = STEM_CATALOGS[parsed.packKey];
  if (!catalog?.has(parsed.stem)) {
    throw new BadRequestException('Unknown sticker');
  }
}
