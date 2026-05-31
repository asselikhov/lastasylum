import { Module, forwardRef } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { AuthModule } from '../auth/auth.module';
import { PushModule } from '../push/push.module';
import { UsersModule } from '../users/users.module';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatController } from './chat.controller';
import { ChatGateway } from './chat.gateway';
import { ChatService } from './chat.service';
import { ChatAttachmentsService } from './chat-attachments.service';
import { ChatRoom, ChatRoomSchema } from './schemas/chat-room.schema';
import {
  ChatAttachment,
  ChatAttachmentSchema,
} from './schemas/chat-attachment.schema';
import { Message, MessageSchema } from './schemas/message.schema';
import {
  ChatRoomReadState,
  ChatRoomReadStateSchema,
} from './schemas/chat-room-read-state.schema';
import {
  OverlayReactionLog,
  OverlayReactionLogSchema,
} from './schemas/overlay-reaction-log.schema';
import {
  OverlayReactionLogReadState,
  OverlayReactionLogReadStateSchema,
} from './schemas/overlay-reaction-log-read-state.schema';
import { OverlayReactionLogService } from './overlay-reaction-log.service';
import { StorageModule } from '../storage/storage.module';
import {
  PlayerTeam,
  PlayerTeamSchema,
} from '../users/schemas/player-team.schema';

@Module({
  imports: [
    forwardRef(() => AuthModule),
    forwardRef(() => PushModule),
    forwardRef(() => UsersModule),
    StorageModule,
    MongooseModule.forFeature([
      { name: Message.name, schema: MessageSchema },
      { name: ChatAttachment.name, schema: ChatAttachmentSchema },
      { name: ChatRoom.name, schema: ChatRoomSchema },
      { name: ChatRoomReadState.name, schema: ChatRoomReadStateSchema },
      { name: OverlayReactionLog.name, schema: OverlayReactionLogSchema },
      {
        name: OverlayReactionLogReadState.name,
        schema: OverlayReactionLogReadStateSchema,
      },
      { name: PlayerTeam.name, schema: PlayerTeamSchema },
    ]),
  ],
  controllers: [ChatController],
  providers: [
    ChatService,
    ChatGateway,
    ChatRoomsService,
    ChatAttachmentsService,
    OverlayReactionLogService,
  ],
  exports: [ChatService, ChatRoomsService, ChatGateway, OverlayReactionLogService],
})
export class ChatModule {}
