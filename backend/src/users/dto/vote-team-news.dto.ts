import { IsString, Length } from 'class-validator';

export class VoteTeamNewsDto {
  @IsString()
  @Length(1, 64)
  optionId: string;
}
