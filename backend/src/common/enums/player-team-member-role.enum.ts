/** In-app squad role (separate from alliance / app role). */
export enum PlayerTeamMemberRole {
  R1 = 'R1',
  R2 = 'R2',
  R3 = 'R3',
  R4 = 'R4',
  R5 = 'R5',
}

export const SQUAD_ROLES_ASSIGNABLE_BY_R4: PlayerTeamMemberRole[] = [
  PlayerTeamMemberRole.R1,
  PlayerTeamMemberRole.R2,
  PlayerTeamMemberRole.R3,
  PlayerTeamMemberRole.R4,
];

export const SQUAD_ROLES_ASSIGNABLE_BY_R5: PlayerTeamMemberRole[] = [
  ...SQUAD_ROLES_ASSIGNABLE_BY_R4,
  PlayerTeamMemberRole.R5,
];

/** @deprecated Use [SQUAD_ROLES_ASSIGNABLE_BY_R5]. */
export const SQUAD_ROLES_ASSIGNABLE_BY_LEADER = SQUAD_ROLES_ASSIGNABLE_BY_R5;

/** R4 and R5 share the same team moderation powers (assign R5 only by R5). */
export function isSquadOfficerRole(
  role: PlayerTeamMemberRole | string | null | undefined,
): boolean {
  const r = (role ?? '').toString().trim().toUpperCase();
  return r === PlayerTeamMemberRole.R4 || r === PlayerTeamMemberRole.R5;
}
