import { IsOptional, IsString, Length, MaxLength } from 'class-validator';

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
  @IsOptional()
  @IsString()
  @MaxLength(4000)
  text?: string;

  @IsOptional()
  @IsString()
  replyToMessageId?: string;

  /** Pre-uploaded via POST …/forum/attachments; must belong to sender and team. */
  @IsOptional()
  @IsString()
  imageFileId?: string;
}

export class UpdateTeamForumMessageDto {
  @IsString()
  @MaxLength(4000)
  text: string;
}
