import { Module } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { ChatModule } from '../chat/chat.module';
import { UsersModule } from '../users/users.module';
import { VoiceGateway } from './voice.gateway';

@Module({
  imports: [AuthModule, ChatModule, UsersModule],
  providers: [VoiceGateway],
})
export class VoiceModule {}
