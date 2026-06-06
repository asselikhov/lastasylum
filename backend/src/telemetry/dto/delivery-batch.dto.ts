import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsInt,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  MinLength,
  ValidateNested,
} from 'class-validator';

export class DeliveryLatencySampleDto {
  @IsString()
  @MinLength(1)
  @MaxLength(64)
  spanType!: string;

  @IsString()
  @MinLength(1)
  @MaxLength(128)
  correlationId!: string;

  @IsInt()
  @Min(0)
  @Max(600_000)
  durationMs!: number;

  @IsString()
  @MinLength(1)
  @MaxLength(32)
  outcome!: string;

  @IsOptional()
  @IsString()
  @MaxLength(128)
  deviceId?: string;
}

export class DeliveryBatchDto {
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(200)
  @ValidateNested({ each: true })
  @Type(() => DeliveryLatencySampleDto)
  samples!: DeliveryLatencySampleDto[];
}
