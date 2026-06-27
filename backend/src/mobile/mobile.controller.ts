import { Controller, Get, UseGuards } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { GamePatchInfo, GamePatchService } from './game-patch.service';

/** Public metadata for sideloaded Android APK distribution (no Play Store). */
@Controller('mobile')
export class MobileController {
  constructor(
    private readonly configService: ConfigService,
    private readonly gamePatchService: GamePatchService,
  ) {}

  @Get('android-update')
  getAndroidUpdate() {
    const raw = this.configService.get<string>('ANDROID_APK_VERSION_CODE');
    const parsed = raw ? parseInt(raw, 10) : 0;
    const versionCode = Number.isFinite(parsed) ? parsed : 0;
    const downloadUrl =
      this.configService.get<string>('ANDROID_APK_DOWNLOAD_URL')?.trim() ||
      null;
    return { versionCode, downloadUrl };
  }

  /**
   * Latest patched game APK from the private GitHub release. Authenticated users
   * only: the GitHub token stays on the backend and we return a short-lived
   * signed download URL plus integrity metadata.
   */
  @UseGuards(JwtAuthGuard)
  @Get('game-patch')
  getGamePatch(): Promise<GamePatchInfo> {
    return this.gamePatchService.getLatestPatch();
  }
}
