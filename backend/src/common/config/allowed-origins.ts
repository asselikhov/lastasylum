/** Parse ALLOWED_ORIGINS env (comma-separated). Empty/undefined => null (caller uses wildcard / permissive default). */
export function parseAllowedOriginsFromEnv(
  raw: string | undefined,
): string[] | null {
  const trimmed = raw?.trim();
  if (!trimmed) {
    return null;
  }
  const list = trimmed
    .split(',')
    .map((o) => o.trim())
    .filter(Boolean);
  return list.length ? list : null;
}
