import { IsString, Length, Matches } from 'class-validator';

export class CreatePlayerTeamDto {
  @IsString()
  @Length(2, 48)
  displayName: string;

  @IsString()
  @Length(3, 3)
  @Matches(/^[\p{L}]{3}$/u, { message: 'Tag must be exactly 3 letters' })
  tag: string;
}
