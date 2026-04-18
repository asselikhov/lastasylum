import { IsString, MaxLength, MinLength } from 'class-validator';

export class RegisterPushTokenDto {
  @IsString()
  @MinLength(10)
  @MaxLength(4096)
  token!: string;
}

export class UpdatePresenceDto {
  @IsString()
  @MinLength(1)
  @MaxLength(32)
  status!: string;
}
