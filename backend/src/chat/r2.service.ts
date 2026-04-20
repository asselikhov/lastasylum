import '../common/aws-r2-sdk-env';
import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';

@Injectable()
export class R2Service {
  private readonly s3: S3Client;
  private readonly bucket: string;

  constructor(private readonly config: ConfigService) {
    const endpoint = this.config
      .getOrThrow<string>('R2_ENDPOINT')
      .trim()
      .replace(/\/+$/, '');
    const accessKeyId = this.config.getOrThrow<string>('R2_ACCESS_KEY_ID').trim();
    const secretAccessKey = this.config.getOrThrow<string>('R2_SECRET_ACCESS_KEY').trim();
    this.bucket = this.config.getOrThrow<string>('R2_BUCKET').trim();
    const region = (this.config.get<string>('R2_REGION') ?? 'auto').trim();

    // AWS SDK ≥3.729 may send default request checksum headers that Cloudflare R2 rejects
    // (500 / InternalError on PutObject). Prefer WHEN_REQUIRED for R2 compatibility.
    // See: https://community.cloudflare.com/t/aws-sdk-client-s3-v3-729-0-breaks-uploadpart-and-putobject-r2-s3-api-compatibility/758637
    // Cast avoids TS mismatch when typings lag behind runtime @aws-sdk options.
    this.s3 = new S3Client({
      region,
      endpoint,
      credentials: { accessKeyId, secretAccessKey },
      requestChecksumCalculation: 'WHEN_REQUIRED',
      responseChecksumValidation: 'WHEN_REQUIRED',
    } as never);
  }

  async putObject(input: {
    key: string;
    body: Buffer;
    contentType: string;
    cacheControl?: string;
  }): Promise<void> {
    await this.s3.send(
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: input.key,
        Body: input.body,
        ContentType: input.contentType,
        CacheControl: input.cacheControl,
      }),
    );
  }

  async getObjectStream(key: string): Promise<{
    body: NodeJS.ReadableStream;
    contentType?: string;
    contentLength?: number;
  }> {
    const res = await this.s3.send(
      new GetObjectCommand({
        Bucket: this.bucket,
        Key: key,
      }),
    );
    if (!res.Body || typeof (res.Body as { pipe?: unknown }).pipe !== 'function') {
      throw new Error('Invalid R2 body stream');
    }
    return {
      body: res.Body as NodeJS.ReadableStream,
      contentType: res.ContentType,
      contentLength: res.ContentLength,
    };
  }
}

