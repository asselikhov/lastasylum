import { IsString, Length, MaxLength } from 'class-validator';

export class CreateTeamForumTopicDto {
  @IsString()
  @Length(1, 200)
  title: string;
}

export class UpdateTeamForumTopicDto {
  @IsString()
  @Length(1, 200)
  title: string;
}

export class CreateTeamForumMessageDto {
  @IsString()
  @MaxLength(4000)
  text: string;
}

export class UpdateTeamForumMessageDto {
  @IsString()
  @MaxLength(4000)
  text: string;
}
