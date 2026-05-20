import { BadRequestException } from '@nestjs/common';

/** Limits concurrent in-memory Multer buffers on Render Free (~512 MB RAM). */
const MAX_CONCURRENT_UPLOADS = 2;

let activeUploads = 0;
const waitQueue: Array<() => void> = [];

export async function withUploadSlot<T>(fn: () => Promise<T>): Promise<T> {
  if (activeUploads >= MAX_CONCURRENT_UPLOADS) {
    await new Promise<void>((resolve) => {
      waitQueue.push(resolve);
    });
  }
  activeUploads += 1;
  try {
    return await fn();
  } finally {
    activeUploads -= 1;
    const next = waitQueue.shift();
    if (next) next();
  }
}

/** Max bytes for a single in-RAM upload on free tier (images / small files). */
export const FREE_TIER_MAX_UPLOAD_BYTES = 64 * 1024 * 1024;

/** Squad forum APK (one slot at a time; still in RAM). */
export const FORUM_APK_MAX_UPLOAD_BYTES = 120 * 1024 * 1024;

export function assertUploadSizeWithinLimit(
  size: number,
  maxBytes: number,
  label = 'file',
): void {
  if (size > maxBytes) {
    throw new BadRequestException(
      `${label} exceeds ${Math.floor(maxBytes / (1024 * 1024))} MB upload limit`,
    );
  }
}
