import type { User } from '../users/schemas/user.schema';

/** Active game identity server number for server-scoped chat rooms. */
export function resolveUserActiveServerNumber(
  user: Pick<User, 'gameIdentities' | 'activeGameIdentityId'>,
): number | null {
  const identities = user.gameIdentities ?? [];
  if (identities.length === 0) return null;
  const activeId = user.activeGameIdentityId;
  const active = activeId
    ? identities.find((g) => g._id?.equals(activeId))
    : identities[0];
  const n = active?.serverNumber;
  return n != null && n >= 1 ? n : null;
}
