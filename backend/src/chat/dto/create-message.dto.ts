import { Transform } from 'class-transformer';
import {
  IsArray,
  IsBoolean,
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

  /** @deprecated Use gameEventAlert (hq_excavation). */
  @IsOptional()
  @IsBoolean()
  excavationAlert?: boolean;

  /** Overlay game event id from [GAME_EVENT_CATALOG] — push + raid alert. */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  gameEventAlert?: string;

  /** Optional client id for HTTP send retry idempotency. */
  @IsOptional()
  @IsString()
  @MaxLength(64)
  clientMessageId?: string;
}
