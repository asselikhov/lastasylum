import { IsMongoId, IsOptional, IsString, MaxLength, MinLength } from 'class-validator';

export class CreateMessageDto {
  @IsString()
  @MinLength(1)
  @MaxLength(1200)
  text: string;

  @IsMongoId()
  roomId: string;

  @IsOptional()
  @IsString()
  allianceId?: string;
}
