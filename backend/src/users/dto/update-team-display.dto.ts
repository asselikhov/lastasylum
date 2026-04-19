import { IsOptional, IsString, MaxLength } from 'class-validator';

/** Full team name + 3-letter tag; both empty clears; both required when setting. */
export class UpdateTeamDisplayNameDto {
  @IsOptional()
  @IsString()
  @MaxLength(64)
  name?: string;

  @IsOptional()
  @IsString()
  @MaxLength(8)
  tag?: string;
}
