import { IsIn, IsString } from 'class-validator';

/** R4 may assign R1–R4; R5 may assign R1–R5 (enforced in [TeamsService.updateMemberSquadRole]). */
export class UpdateSquadMemberRoleDto {
  @IsString()
  @IsIn(['R1', 'R2', 'R3', 'R4', 'R5'])
  role!: string;
}
