import { IsBoolean, IsOptional, IsString, MaxLength } from 'class-validator';

export class UpdateNotificationPreferencesDto {
  /** @deprecated Use gameEventId + enabled for hq_excavation. */
  @IsOptional()
  @IsBoolean()
  excavationPushEnabled?: boolean;

  @IsOptional()
  @IsString()
  @MaxLength(64)
  gameEventId?: string;

  @IsOptional()
  @IsBoolean()
  enabled?: boolean;
}
