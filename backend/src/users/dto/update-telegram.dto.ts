import { IsOptional, IsString, MaxLength } from 'class-validator';

/** Telegram username without @; omit or empty string to clear. */
export class UpdateTelegramDto {
  @IsOptional()
  @IsString()
  @MaxLength(64)
  username?: string;
}
