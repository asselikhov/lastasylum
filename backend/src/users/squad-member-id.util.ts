import { Types } from 'mongoose';

/** Compare squad member userId stored as ObjectId or legacy string. */
export function squadMemberUserIdEquals(
  stored: unknown,
  userId: string,
): boolean {
  if (stored instanceof Types.ObjectId) {
    return Types.ObjectId.isValid(userId) && stored.equals(userId);
  }
  if (typeof stored === 'string') {
    return stored === userId;
  }
  if (stored == null) return false;
  return String(stored) === userId;
}

export function squadMemberUserIdString(stored: unknown): string {
  if (stored instanceof Types.ObjectId) return stored.toString();
  return String(stored ?? '');
}

/** Normalize attachment uploader id from Mongo (string or ObjectId). */
export function attachmentUploaderIdEquals(
  stored: unknown,
  expectedUserId: string,
): boolean {
  if (stored instanceof Types.ObjectId) {
    return stored.toString() === expectedUserId;
  }
  return String(stored ?? '') === expectedUserId;
}
