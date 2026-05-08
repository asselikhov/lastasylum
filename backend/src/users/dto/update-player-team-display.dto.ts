import { IsString, MaxLength, MinLength } from 'class-validator';

export class UpdatePlayerTeamDisplayNameDto {
  @IsString()
  @MinLength(2)
  @MaxLength(48)
  displayName!: string;

  @IsString()
  @MinLength(3)
  @MaxLength(4)
  tag!: string;
}
