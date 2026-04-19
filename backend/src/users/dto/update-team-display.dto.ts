import { IsOptional, IsString, MaxLength } from 'class-validator';

/** Cosmetic team name shown in profile; omit or empty string clears. */
export class UpdateTeamDisplayNameDto {
  @IsOptional()
  @IsString()
  @MaxLength(64)
  name?: string;
}
