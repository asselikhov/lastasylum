import { Module, forwardRef } from '@nestjs/common';
import { UsersModule } from '../users/users.module';
import { PushNotificationsService } from './push-notifications.service';

@Module({
  imports: [forwardRef(() => UsersModule)],
  providers: [PushNotificationsService],
  exports: [PushNotificationsService],
})
export class PushModule {}
