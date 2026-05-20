import { Module, forwardRef } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { TeamPresenceGateway } from './team-presence.gateway';
import { UsersModule } from './users.module';

@Module({
  imports: [
    forwardRef(() => AuthModule),
    forwardRef(() => UsersModule),
  ],
  providers: [TeamPresenceGateway],
  exports: [TeamPresenceGateway],
})
export class TeamPresenceSocketModule {}
