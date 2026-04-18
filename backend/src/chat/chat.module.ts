import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { AuthModule } from '../auth/auth.module';
import { PushModule } from '../push/push.module';
import { UsersModule } from '../users/users.module';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatController } from './chat.controller';
import { ChatGateway } from './chat.gateway';
import { ChatService } from './chat.service';
import { ChatRoom, ChatRoomSchema } from './schemas/chat-room.schema';
import { Message, MessageSchema } from './schemas/message.schema';

@Module({
  imports: [
    AuthModule,
    PushModule,
    UsersModule,
    MongooseModule.forFeature([
      { name: Message.name, schema: MessageSchema },
      { name: ChatRoom.name, schema: ChatRoomSchema },
    ]),
  ],
  controllers: [ChatController],
  providers: [ChatService, ChatGateway, ChatRoomsService],
  exports: [ChatService, ChatRoomsService],
})
export class ChatModule {}
