import { IsString, MaxLength, MinLength } from 'class-validator';

export class UpdatePlayerTeamDisplayNameDto {
  @IsString()
  @MinLength(2)
  @MaxLength(48)
  displayName!: string;
}
