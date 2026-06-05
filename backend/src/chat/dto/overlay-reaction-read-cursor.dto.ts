import { IsNotEmpty, IsString, MaxLength } from 'class-validator';

export class OverlayReactionReadCursorDto {
  @IsString()
  @IsNotEmpty()
  @MaxLength(128)
  lastSeenLogId!: string;
}
