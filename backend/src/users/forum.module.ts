import { Module, forwardRef } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { ChatModule } from '../chat/chat.module';
import { StorageModule } from '../storage/storage.module';
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

@Module({
  imports: [
    StorageModule,
    forwardRef(() => ChatModule),
    forwardRef(() => TeamForumSocketModule),
    MongooseModule.forFeature([
      { name: TeamForumTopic.name, schema: TeamForumTopicSchema },
      { name: TeamForumMessage.name, schema: TeamForumMessageSchema },
      {
        name: TeamForumTopicReadState.name,
        schema: TeamForumTopicReadStateSchema,
      },
    ]),
  ],
  providers: [TeamForumService],
  exports: [TeamForumService, MongooseModule],
})
export class ForumModule {}
