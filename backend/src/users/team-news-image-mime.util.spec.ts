import { BadRequestException } from '@nestjs/common';
import {
  resolveTeamNewsImageMime,
  sniffImageMimeFromBuffer,
} from './team-news-image-mime.util';

describe('team-news-image-mime.util', () => {
  it('sniffs jpeg', () => {
    const buf = Buffer.from([0xff, 0xd8, 0xff, 0xe0]);
    expect(sniffImageMimeFromBuffer(buf)).toBe('image/jpeg');
  });

  it('accepts reported image/* mime', () => {
    const buf = Buffer.from([0x00]);
    expect(resolveTeamNewsImageMime(buf, 'image/png')).toBe('image/png');
  });

  it('sniffs when multer sends octet-stream', () => {
    const buf = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00]);
    expect(resolveTeamNewsImageMime(buf, 'application/octet-stream')).toBe(
      'image/jpeg',
    );
  });

  it('throws when mime missing and bytes are not an image', () => {
    const buf = Buffer.from('hello');
    expect(() => resolveTeamNewsImageMime(buf, undefined)).toThrow(
      BadRequestException,
    );
  });
});
