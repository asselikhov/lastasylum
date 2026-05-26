import { Module, forwardRef } from '@nestjs/common';
import { AuthModule } from '../auth/auth.module';
import { TeamForumGateway } from './team-forum.gateway';
import { UsersModule } from './users.module';

@Module({
  imports: [forwardRef(() => AuthModule), forwardRef(() => UsersModule)],
  providers: [TeamForumGateway],
  exports: [TeamForumGateway],
})
export class TeamForumSocketModule {}
