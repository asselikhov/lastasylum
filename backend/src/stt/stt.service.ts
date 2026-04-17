import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class SttService {
  private readonly logger = new Logger(SttService.name);

  constructor(private readonly configService: ConfigService) {}

  async transcribe(file: Express.Multer.File): Promise<string> {
    const apiKey = this.configService.get<string>('OPENAI_API_KEY');
    if (!apiKey) {
      return this.mockTranscribe(file);
    }

    const model =
      this.configService.get<string>('OPENAI_STT_MODEL') ?? 'whisper-1';
    const fileName = file.originalname || 'voice.m4a';
    const mime = file.mimetype || 'audio/mp4';

    const formData = new FormData();
    formData.append('model', model);
    const bytes = file.buffer ?? Buffer.alloc(0);
    const u8 = new Uint8Array(bytes);
    formData.append('file', new Blob([u8], { type: mime }), fileName);

    const response = await fetch(
      'https://api.openai.com/v1/audio/transcriptions',
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${apiKey}`,
        },
        body: formData,
      },
    );

    if (!response.ok) {
      const body = await response.text();
      this.logger.warn(`OpenAI STT error ${response.status}: ${body}`);
      throw new Error(`Speech recognition failed (${response.status})`);
    }

    const data = (await response.json()) as { text?: string };
    const text = data.text?.trim() ?? '';
    if (!text) {
      throw new Error('Empty transcription result');
    }
    return text;
  }

  private mockTranscribe(file: Express.Multer.File): string {
    const kb = Math.max(1, Math.round((file.size ?? 0) / 1024));
    const base =
      file.originalname?.replace(/\.[^/.]+$/, '') ?? `ptt-${Date.now()}`;
    return `[voice ${kb}KB] ${base}`;
  }
}
