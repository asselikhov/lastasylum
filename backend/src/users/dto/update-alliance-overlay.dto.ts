import { IsBoolean } from 'class-validator';

export class UpdateAllianceOverlayDto {
  @IsBoolean()
  overlayEnabled: boolean;
}
