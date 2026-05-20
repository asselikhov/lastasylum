import type { User } from '../users/schemas/user.schema';

function identityId(g: { _id?: { toString(): string } }): string {
  return g._id?.toString() ?? '';
}

/** Active game identity server (aligned with [GameIdentitiesService.getActiveIdentity]). */
export function resolveUserActiveServerNumber(
  user: Pick<User, 'gameIdentities' | 'activeGameIdentityId'>,
): number | null {
  const identities = user.gameIdentities ?? [];
  if (identities.length === 0) return null;
  const activeId = user.activeGameIdentityId?.toString();
  let active = activeId
    ? identities.find((g) => identityId(g) === activeId)
    : undefined;
  if (!active) {
    active = identities[0];
  }
  const n = active?.serverNumber;
  return n != null && n >= 1 ? n : null;
}
