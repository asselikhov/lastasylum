import type { ServiceAccount } from 'firebase-admin';

/**
 * Parse FIREBASE_SERVICE_ACCOUNT_JSON from Render/env.
 * Handles single-line JSON, outer quotes, and literal newlines in private_key.
 */
export function parseFirebaseServiceAccountJson(raw: string): ServiceAccount {
  const trimmed = raw.trim();
  const attempts: string[] = [trimmed];
  if (
    (trimmed.startsWith("'") && trimmed.endsWith("'")) ||
    (trimmed.startsWith('"') && trimmed.endsWith('"'))
  ) {
    attempts.push(trimmed.slice(1, -1));
  }
  attempts.push(trimmed.replace(/\r?\n/g, '\\n'));
  let lastError: Error | undefined;
  for (const candidate of attempts) {
    try {
      const parsed = JSON.parse(candidate) as ServiceAccount & {
        private_key?: string;
      };
      if (
        typeof parsed.private_key === 'string' &&
        parsed.private_key.includes('\\n')
      ) {
        parsed.private_key = parsed.private_key.replace(/\\n/g, '\n');
      }
      return parsed;
    } catch (e) {
      lastError = e as Error;
    }
  }
  throw lastError ?? new Error('invalid FIREBASE_SERVICE_ACCOUNT_JSON');
}
