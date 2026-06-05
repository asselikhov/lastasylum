import { Module, forwardRef } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { ChatModule } from '../chat/chat.module';
import { StorageModule } from '../storage/storage.module';
import { User, UserSchema } from './schemas/user.schema';
import { UsersModule } from './users.module';
import {
  TeamForumMessage,
  TeamForumMessageSchema,
} from './schemas/team-forum-message.schema';
import {
  TeamForumTopic,
  TeamForumTopicSchema,
} from './schemas/team-forum-topic.schema';
import {
  TeamForumTopicReadState,
  TeamForumTopicReadStateSchema,
} from './schemas/team-forum-topic-read-state.schema';
import { TeamForumService } from './team-forum.service';
import { TeamForumSocketModule } from './team-forum-socket.module';
import {
  PinAuditLog,
  PinAuditLogSchema,
} from './schemas/pin-audit-log.schema';
import { PinAuditService } from './pin-audit.service';

@Module({
  imports: [
    StorageModule,
    forwardRef(() => UsersModule),
    forwardRef(() => ChatModule),
    forwardRef(() => TeamForumSocketModule),
    MongooseModule.forFeature([
      { name: User.name, schema: UserSchema },
      { name: TeamForumTopic.name, schema: TeamForumTopicSchema },
      { name: TeamForumMessage.name, schema: TeamForumMessageSchema },
      {
        name: TeamForumTopicReadState.name,
        schema: TeamForumTopicReadStateSchema,
      },
      { name: PinAuditLog.name, schema: PinAuditLogSchema },
    ]),
  ],
  providers: [TeamForumService, PinAuditService],
  exports: [TeamForumService, PinAuditService, MongooseModule],
})
export class ForumModule {}
