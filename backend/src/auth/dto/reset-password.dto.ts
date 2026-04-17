import { IsEmail, IsString, MaxLength, MinLength } from 'class-validator';

export class ResetPasswordDto {
  @IsEmail()
  email: string;

  @IsString()
  @MinLength(64)
  @MaxLength(64)
  token: string;

  @IsString()
  @MinLength(8)
  @MaxLength(200)
  newPassword: string;
}
