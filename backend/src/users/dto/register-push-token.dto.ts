import { IsIn, IsString, MaxLength, MinLength } from 'class-validator';

export class RegisterPushTokenDto {
  @IsString()
  @MinLength(10)
  @MaxLength(4096)
  token!: string;
}

const PRESENCE_STATUSES = ['ingame', 'online', 'away'] as const;

export class UpdatePresenceDto {
  @IsString()
  @IsIn(PRESENCE_STATUSES)
  @MaxLength(32)
  status!: string;
}
