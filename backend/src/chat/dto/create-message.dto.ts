import {
  IsArray,
  IsMongoId,
  IsOptional,
  IsString,
  MaxLength,
  MinLength,
} from 'class-validator';

export class CreateMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(1200)
  text: string;

  @IsMongoId()
  roomId: string;

  @IsOptional()
  @IsString()
  allianceId?: string;

  @IsOptional()
  @IsMongoId()
  replyToMessageId?: string;

  /** GridFS file ids returned by POST /chat/attachments (images only for now). */
  @IsOptional()
  @IsArray()
  @IsMongoId({ each: true })
  attachments?: string[];
}
