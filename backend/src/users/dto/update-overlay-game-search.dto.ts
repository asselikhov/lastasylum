import { IsBoolean } from 'class-validator';

export class UpdateOverlayGameSearchDto {
  @IsBoolean()
  overlayGameSearchEnabled: boolean;
}
