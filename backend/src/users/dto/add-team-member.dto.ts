import { IsString, Length, Matches } from 'class-validator';

export class AddTeamMemberDto {
  @IsString()
  @Length(3, 32)
  @Matches(/^[a-zA-Z0-9_\p{L}]+$/u)
  username: string;
}
