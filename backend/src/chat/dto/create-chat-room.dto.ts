import { IsNumber, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateChatRoomDto {
  @IsString()
  @MinLength(1)
  @MaxLength(80)
  title: string;

  @IsOptional()
  @IsNumber()
  sortOrder?: number;
}
