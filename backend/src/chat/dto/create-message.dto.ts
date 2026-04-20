import { Transform } from 'class-transformer';
import {
  IsArray,
  IsMongoId,
  IsOptional,
  IsString,
  MaxLength,
} from 'class-validator';

export class CreateMessageDto {
  /** Omitted or blank when sending image-only (requires `attachments`). */
  @Transform(({ value }) =>
    typeof value === 'string' && value.trim() === '' ? undefined : value,
  )
  @IsOptional()
  @IsString()
  @MaxLength(1200)
  text?: string;

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
