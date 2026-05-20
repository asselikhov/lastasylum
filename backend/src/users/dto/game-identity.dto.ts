import { Type } from 'class-transformer';
import {
  IsInt,
  IsMongoId,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength,
} from 'class-validator';

export class CreateGameIdentityDto {
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(9999)
  serverNumber: number;

  @IsString()
  @MinLength(2)
  @MaxLength(32)
  gameNickname: string;
}

export class UpdateGameIdentityDto {
  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(9999)
  serverNumber?: number;

  @IsOptional()
  @IsString()
  @MinLength(2)
  @MaxLength(32)
  gameNickname?: string;
}

export class SwitchActiveGameIdentityDto {
  @IsMongoId()
  gameIdentityId: string;
}
