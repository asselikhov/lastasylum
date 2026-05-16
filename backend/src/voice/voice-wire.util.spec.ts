import {
  packDownstreamFrame,
  packUpstreamFrame,
  parseDownstreamFrame,
  parseUpstreamFrame,
  VOICE_CODEC_OPUS,
} from './voice-wire.util';

describe('voice-wire.util', () => {
  it('round-trips upstream frame', () => {
    const payload = Buffer.from([1, 2, 3, 4]);
    const packed = packUpstreamFrame(VOICE_CODEC_OPUS, 42, payload);
    const parsed = parseUpstreamFrame(packed);
    expect(parsed).not.toBeNull();
    expect(parsed!.codec).toBe(VOICE_CODEC_OPUS);
    expect(parsed!.seq).toBe(42);
    expect(parsed!.payload.equals(payload)).toBe(true);
  });

  it('round-trips downstream frame', () => {
    const payload = Buffer.from([9, 8, 7]);
    const packed = packDownstreamFrame('user-abc', VOICE_CODEC_OPUS, 7, payload);
    const parsed = parseDownstreamFrame(packed);
    expect(parsed).not.toBeNull();
    expect(parsed!.userId).toBe('user-abc');
    expect(parsed!.seq).toBe(7);
    expect(parsed!.payload.equals(payload)).toBe(true);
  });
});
