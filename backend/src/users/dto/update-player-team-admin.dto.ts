import { IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdatePlayerTeamAdminDto {
  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(48)
  displayName?: string;

  @IsOptional()
  @IsString()
  @MinLength(3)
  @MaxLength(4)
  tag?: string;
}
