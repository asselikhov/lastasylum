import { Module } from '@nestjs/common';
import { UsersModule } from '../users/users.module';
import { PushNotificationsService } from './push-notifications.service';

@Module({
  imports: [UsersModule],
  providers: [PushNotificationsService],
  exports: [PushNotificationsService],
})
export class PushModule {}
