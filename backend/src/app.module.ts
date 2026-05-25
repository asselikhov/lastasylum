import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MongooseModule } from '@nestjs/mongoose';
import { AppController } from './app.controller';
import { AppService } from './app.service';
import { AuthModule } from './auth/auth.module';
import { ChatModule } from './chat/chat.module';
import { MobileModule } from './mobile/mobile.module';
import { UsersModule } from './users/users.module';
import { VoiceModule } from './voice/voice.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
    }),
    MongooseModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (configService: ConfigService) => ({
        uri: configService.getOrThrow<string>('MONGODB_URI'),
        dbName: configService.get<string>('MONGODB_DB_NAME') ?? 'last_asylum',
      }),
    }),
    AuthModule,
    UsersModule,
    ChatModule,
    VoiceModule,
    MobileModule,
  ],
  controllers: [AppController],
  providers: [AppService],
})
export class AppModule {}
