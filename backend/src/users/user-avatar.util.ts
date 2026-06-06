/** Public relative URL for a user's profile avatar (null when none). */
export function buildAvatarRelativeUrl(
  userId: string,
  avatarKey: string | null | undefined,
  avatarUpdatedAt: Date | null | undefined,
): string | null {
  if (!avatarKey?.trim() || !avatarUpdatedAt) return null;
  return `/users/avatars/${userId}?v=${avatarUpdatedAt.getTime()}`;
}
