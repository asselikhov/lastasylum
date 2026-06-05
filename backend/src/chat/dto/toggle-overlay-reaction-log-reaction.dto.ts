import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class ToggleOverlayReactionLogReactionDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(32)
  emoji!: string;
}
