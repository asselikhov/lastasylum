import { Type } from 'class-transformer';
import {
  IsEmail,
  IsEnum,
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';
import { AllianceRole } from '../../common/enums/alliance-role.enum';

export class RegisterDto {
  /** Логин аккаунта SquadRelay (уникальный). */
  @IsString()
  @MinLength(3)
  username: string;

  @IsEmail()
  email: string;

  @IsString()
  @MinLength(8)
  password: string;

  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(9999)
  serverNumber: number;

  @IsString()
  @MinLength(2)
  @MaxLength(32)
  gameNickname: string;

  @IsOptional()
  @IsEnum(AllianceRole)
  role?: AllianceRole;
}
