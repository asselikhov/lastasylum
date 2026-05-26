import { BadRequestException } from '@nestjs/common';

/** Sniff common image formats from the first bytes (Android may send octet-stream or omit MIME). */
export function sniffImageMimeFromBuffer(buf: Buffer): string | null {
  if (buf.length < 4) return null;
  if (buf[0] === 0xff && buf[1] === 0xd8) return 'image/jpeg';
  if (
    buf[0] === 0x89 &&
    buf[1] === 0x50 &&
    buf[2] === 0x4e &&
    buf[3] === 0x47
  ) {
    return 'image/png';
  }
  if (buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) return 'image/gif';
  if (
    buf[0] === 0x52 &&
    buf[1] === 0x49 &&
    buf[2] === 0x46 &&
    buf[3] === 0x46
  ) {
    return 'image/webp';
  }
  if (buf.length >= 12) {
    const brand = buf.toString('ascii', 4, 8);
    if (brand === 'ftyp') {
      const major = buf.toString('ascii', 8, 12);
      if (
        major.includes('heic') ||
        major.includes('heix') ||
        major.includes('mif1')
      ) {
        return 'image/heic';
      }
      return 'image/heic';
    }
  }
  return null;
}

export function resolveTeamNewsImageMime(
  buffer: Buffer,
  reportedMime?: string | null,
): string {
  const raw = (reportedMime ?? '').trim().toLowerCase();
  if (raw.startsWith('image/')) return raw;
  const sniffed = sniffImageMimeFromBuffer(buffer);
  if (sniffed) return sniffed;
  throw new BadRequestException('Only image uploads are supported');
}
