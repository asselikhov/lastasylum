import { Injectable } from '@nestjs/common';

type EligibleUsersCacheEntry = {
  userIds: string[];
  until: number;
};

/** Room-scoped eligible user list cache shared by gateway and roster invalidation. */
@Injectable()
export class ChatEligibleUsersCacheService {
  static readonly CACHE_MS = 3_000;

  private readonly cache = new Map<string, EligibleUsersCacheEntry>();

  get(roomId: string): EligibleUsersCacheEntry | undefined {
    const rid = roomId.trim();
    if (!rid) return undefined;
    return this.cache.get(rid);
  }

  set(roomId: string, userIds: string[], until: number): void {
    const rid = roomId.trim();
    if (!rid) return;
    this.cache.set(rid, { userIds, until });
  }

  invalidate(roomId?: string): void {
    const rid = roomId?.trim();
    if (rid) {
      this.cache.delete(rid);
      return;
    }
    this.cache.clear();
  }
}
