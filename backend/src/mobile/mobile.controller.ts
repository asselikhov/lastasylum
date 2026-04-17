import { Controller, Get } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

/** Public metadata for sideloaded Android APK distribution (no Play Store). */
@Controller('mobile')
export class MobileController {
  constructor(private readonly configService: ConfigService) {}

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
}
