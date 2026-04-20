import { IsIn, IsString } from 'class-validator';

/** Leader may assign R1–R4 only (R5 is reserved for the squad creator). */
export class UpdateSquadMemberRoleDto {
  @IsString()
  @IsIn(['R1', 'R2', 'R3', 'R4'])
  role!: string;
}
