import { MailerModule } from '@nestjs-modules/mailer';
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { JwtModule } from '@nestjs/jwt';
import { PassportModule } from '@nestjs/passport';
import type { StringValue } from 'ms';
import { UsersModule } from '../users/users.module';
import { AuthController } from './auth.controller';
import { AuthService } from './auth.service';
import { JwtStrategy } from './jwt.strategy';

@Module({
  imports: [
    ConfigModule,
    UsersModule,
    MailerModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => {
        const host = config.get<string>('SMTP_HOST')?.trim();
        const from =
          config.get<string>('SMTP_FROM')?.trim() || 'noreply@localhost';
        if (!host) {
          return {
            transport: { jsonTransport: true },
            defaults: { from },
          };
        }
        const port = Number(config.get<string>('SMTP_PORT')) || 587;
        const secure = config.get<string>('SMTP_SECURE') === 'true';
        const user = config.get<string>('SMTP_USER')?.trim();
        const pass = config.get<string>('SMTP_PASS');
        return {
          transport: {
            host,
            port,
            secure,
            auth:
              user && pass !== undefined && pass !== ''
                ? { user, pass }
                : undefined,
          },
          defaults: { from },
        };
      },
    }),
    PassportModule.register({ defaultStrategy: 'jwt' }),
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        secret: configService.getOrThrow<string>('JWT_SECRET'),
        signOptions: {
          expiresIn: (configService.get<string>('JWT_EXPIRES_IN') ??
            '7d') as StringValue,
        },
      }),
    }),
  ],
  controllers: [AuthController],
  providers: [AuthService, JwtStrategy],
  exports: [AuthService, PassportModule, JwtModule],
})
export class AuthModule {}
