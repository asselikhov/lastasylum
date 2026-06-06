import {
  IsArray,
  IsMongoId,
  IsOptional,
  IsString,
  Length,
  MaxLength,
  ValidateIf,
} from 'class-validator';

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

  /** Multiple pre-uploaded attachments (preferred). */
  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  imageFileIds?: string[];

  /** Pre-uploaded APK/file via POST …/forum/attachments/file (squad R4/R5). */
  @IsOptional()
  @IsString()
  fileFileId?: string;

  /** Client idempotency key for retry-safe sends (max 64 chars). */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  clientMessageId?: string;
}

export class UpdateTeamForumMessageDto {
  @IsString()
  @MaxLength(4000)
  text: string;
}

export class MarkTeamForumTopicReadDto {
  @IsString()
  messageId: string;
}

/** PUT /teams/:teamId/forum/topics/:topicId/pin */
export class PinTeamForumTopicMessageDto {
  @IsOptional()
  @ValidateIf((_, value) => value != null)
  @IsMongoId()
  messageId?: string | null;
}

export class BulkDeleteTeamForumMessagesDto {
  @IsOptional()
  @IsString()
  /** Optional UI trace label; ignored by backend. */
  reason?: string;

  @IsArray()
  @IsString({ each: true })
  messageIds: string[];
}
