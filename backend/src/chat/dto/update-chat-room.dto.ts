import { IsBoolean, IsNumber, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class UpdateChatRoomDto {
  @IsOptional()
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  title?: string;

  @IsOptional()
  @IsNumber()
  sortOrder?: number;

  @IsOptional()
  @IsBoolean()
  archived?: boolean;
}
