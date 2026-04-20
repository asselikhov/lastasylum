import './common/aws-r2-sdk-env';
import { ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { parseAllowedOriginsFromEnv } from './common/config/allowed-origins';
import { AppModule } from './app.module';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
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
