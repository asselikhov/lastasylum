import { AllianceRole } from './enums/alliance-role.enum';

const LEGACY_ALLIANCE_ROLE_MAP: Record<string, AllianceRole> = {
  R2: AllianceRole.MEMBER,
  R3: AllianceRole.OFFICER,
  R4: AllianceRole.MODERATOR,
  R5: AllianceRole.ADMIN,
};

export function normalizeAllianceRole(
  raw: string | AllianceRole | null | undefined,
): AllianceRole {
  const v = (raw ?? '').toString().trim().toUpperCase();
  if (Object.values(AllianceRole).includes(v as AllianceRole)) {
    return v as AllianceRole;
  }
  return LEGACY_ALLIANCE_ROLE_MAP[v] ?? AllianceRole.MEMBER;
}

export function isAppAdminRole(
  raw: string | AllianceRole | null | undefined,
): boolean {
  return normalizeAllianceRole(raw) === AllianceRole.ADMIN;
}

export const LEGACY_ALLIANCE_ROLE_MIGRATION: ReadonlyArray<{
  from: string;
  to: AllianceRole;
}> = [
  { from: 'R2', to: AllianceRole.MEMBER },
  { from: 'R3', to: AllianceRole.OFFICER },
  { from: 'R4', to: AllianceRole.MODERATOR },
  { from: 'R5', to: AllianceRole.ADMIN },
];
