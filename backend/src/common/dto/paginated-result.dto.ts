/** Standard cursor-free page for admin lists (Render-friendly). */
export type PaginatedResult<T> = {
  items: T[];
  total: number;
  skip: number;
  limit: number;
  hasMore: boolean;
};

export function paginateParams(
  skipRaw?: number,
  limitRaw?: number,
  defaults?: { limit?: number; max?: number },
): { skip: number; limit: number } {
  const max = defaults?.max ?? 200;
  const defaultLimit = defaults?.limit ?? 50;
  const skip = Math.max(0, Math.floor(skipRaw ?? 0));
  const limit = Math.min(
    max,
    Math.max(1, Math.floor(limitRaw ?? defaultLimit)),
  );
  return { skip, limit };
}
