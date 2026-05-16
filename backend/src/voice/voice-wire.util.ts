/** Binary voice frame wire format (Socket.IO payload as Buffer). */

export const VOICE_CODEC_OPUS = 0;
export const VOICE_CODEC_PCM = 1;

export const MAX_VOICE_USER_ID_BYTES = 64;
export const MAX_VOICE_FRAME_BYTES = 4_096;

const UP_HEADER_BYTES = 5; // codec + seq(u16) + len(u16)

export type ParsedUpstreamFrame = {
  codec: number;
  seq: number;
  payload: Buffer;
};

export type ParsedDownstreamFrame = {
  userId: string;
  codec: number;
  seq: number;
  payload: Buffer;
};

export function packUpstreamFrame(
  codec: number,
  seq: number,
  payload: Buffer,
): Buffer {
  if (payload.length > MAX_VOICE_FRAME_BYTES) {
    throw new Error('Voice frame too large');
  }
  const out = Buffer.allocUnsafe(UP_HEADER_BYTES + payload.length);
  out[0] = codec & 0xff;
  out.writeUInt16BE(seq & 0xffff, 1);
  out.writeUInt16BE(payload.length, 3);
  payload.copy(out, UP_HEADER_BYTES);
  return out;
}

export function parseUpstreamFrame(data: Buffer): ParsedUpstreamFrame | null {
  if (!Buffer.isBuffer(data) || data.length < UP_HEADER_BYTES) {
    return null;
  }
  const codec = data[0];
  if (codec !== VOICE_CODEC_OPUS && codec !== VOICE_CODEC_PCM) {
    return null;
  }
  const seq = data.readUInt16BE(1);
  const len = data.readUInt16BE(3);
  if (len <= 0 || len > MAX_VOICE_FRAME_BYTES) return null;
  if (data.length < UP_HEADER_BYTES + len) return null;
  const payload = data.subarray(UP_HEADER_BYTES, UP_HEADER_BYTES + len);
  return { codec, seq, payload };
}

export function packDownstreamFrame(
  userId: string,
  codec: number,
  seq: number,
  payload: Buffer,
): Buffer {
  const userBytes = Buffer.from(userId, 'utf8');
  if (userBytes.length === 0 || userBytes.length > MAX_VOICE_USER_ID_BYTES) {
    throw new Error('Invalid voice userId');
  }
  if (payload.length > MAX_VOICE_FRAME_BYTES) {
    throw new Error('Voice frame too large');
  }
  const headerLen = 1 + userBytes.length + 1 + 4; // userLen + userId + codec + seq + payloadLen
  const out = Buffer.allocUnsafe(headerLen + payload.length);
  let o = 0;
  out[o++] = userBytes.length;
  userBytes.copy(out, o);
  o += userBytes.length;
  out[o++] = codec & 0xff;
  out.writeUInt16BE(seq & 0xffff, o);
  o += 2;
  out.writeUInt16BE(payload.length, o);
  o += 2;
  payload.copy(out, o);
  return out;
}

export function parseDownstreamFrame(data: Buffer): ParsedDownstreamFrame | null {
  if (!Buffer.isBuffer(data) || data.length < 7) return null;
  const userLen = data[0];
  if (userLen <= 0 || userLen > MAX_VOICE_USER_ID_BYTES) return null;
  const headerLen = 1 + userLen + 1 + 4;
  if (data.length < headerLen) return null;
  const userId = data.subarray(1, 1 + userLen).toString('utf8');
  const codec = data[1 + userLen];
  if (codec !== VOICE_CODEC_OPUS && codec !== VOICE_CODEC_PCM) return null;
  const seq = data.readUInt16BE(1 + userLen + 1);
  const len = data.readUInt16BE(1 + userLen + 3);
  if (len <= 0 || len > MAX_VOICE_FRAME_BYTES) return null;
  if (data.length < headerLen + len) return null;
  const payload = data.subarray(headerLen, headerLen + len);
  return { userId, codec, seq, payload };
}
