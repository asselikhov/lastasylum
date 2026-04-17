import { Injectable } from '@nestjs/common';

@Injectable()
export class SttService {
  transcribe(file: Express.Multer.File): string {
    const kb = Math.max(1, Math.round((file.size ?? 0) / 1024));
    const base =
      file.originalname?.replace(/\.[^/.]+$/, '') ?? `ptt-${Date.now()}`;
    return `[local-stt-disabled ${kb}KB] ${base}`;
  }
}
