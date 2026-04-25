import { Module } from '@nestjs/common';
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
import { R2Service } from './r2.service';

@Module({
  imports: [
    AuthModule,
    PushModule,
    UsersModule,
    MongooseModule.forFeature([
      { name: Message.name, schema: MessageSchema },
      { name: ChatAttachment.name, schema: ChatAttachmentSchema },
      { name: ChatRoom.name, schema: ChatRoomSchema },
    ]),
  ],
  controllers: [ChatController],
  providers: [
    ChatService,
    ChatGateway,
    ChatRoomsService,
    ChatAttachmentsService,
    R2Service,
  ],
  exports: [ChatService, ChatRoomsService],
})
export class ChatModule {}
