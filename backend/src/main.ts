import './common/aws-r2-sdk-env';
import { Logger, ValidationPipe, type INestApplication } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { parseAllowedOriginsFromEnv } from './common/config/allowed-origins';
import { RedisIoAdapter } from './common/redis-io.adapter';
import { AppModule } from './app.module';

const SLOW_REQUEST_MS = 2_000;

function installSlowRequestLogger(app: INestApplication): void {
  const logger = new Logger('SlowRequest');
  const http = app.getHttpAdapter().getInstance();
  http.use(
    (
      req: { method?: string; url?: string },
      res: { on: Function },
      next: () => void,
    ) => {
      const started = Date.now();
      res.on('finish', () => {
        const ms = Date.now() - started;
        if (ms >= SLOW_REQUEST_MS) {
          logger.warn(`${req.method ?? '?'} ${req.url ?? '?'} ${ms}ms`);
        }
      });
      next();
    },
  );
}

async function bootstrap() {
  // SCALE: Redis Socket.IO adapter when REDIS_URL is set (multi-replica deploy).
  const app = await NestFactory.create(AppModule);
  const redisUrl = process.env.REDIS_URL?.trim();
  if (redisUrl) {
    const redisAdapter = new RedisIoAdapter(app, redisUrl);
    await redisAdapter.connectToRedis();
    app.useWebSocketAdapter(redisAdapter);
  }
  installSlowRequestLogger(app);
  const config = app.get(ConfigService);
  const origins = parseAllowedOriginsFromEnv(
    config.get<string>('ALLOWED_ORIGINS'),
  );
  app.enableCors({
    origin: origins ?? '*',
  });
  app.useGlobalPipes(
    new ValidationPipe({
      whitelist: true,
      forbidNonWhitelisted: true,
      transform: true,
    }),
  );
  await app.listen(process.env.PORT ?? 3000);
}
void bootstrap();
