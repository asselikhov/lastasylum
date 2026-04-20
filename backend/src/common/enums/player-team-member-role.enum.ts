/** In-app squad role (separate from alliance / app role). */
export enum PlayerTeamMemberRole {
  R1 = 'R1',
  R2 = 'R2',
  R3 = 'R3',
  R4 = 'R4',
  R5 = 'R5',
}

export const SQUAD_ROLES_ASSIGNABLE_BY_LEADER: PlayerTeamMemberRole[] = [
  PlayerTeamMemberRole.R1,
  PlayerTeamMemberRole.R2,
  PlayerTeamMemberRole.R3,
  PlayerTeamMemberRole.R4,
];
