import { IsString, Length, Matches } from 'class-validator';

export class CreatePlayerTeamDto {
  @IsString()
  @Length(2, 48)
  displayName: string;

  @IsString()
  @Length(3, 4)
  @Matches(/^[\p{L}]{3,4}$/u, { message: 'Tag must be 3-4 letters' })
  tag: string;
}
