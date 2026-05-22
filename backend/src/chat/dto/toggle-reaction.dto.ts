import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class ToggleReactionDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(32)
  emoji: string;
}
