import { BadRequestException } from '@nestjs/common';
import { getModelToken } from '@nestjs/mongoose';
import { Test } from '@nestjs/testing';
import { Types } from 'mongoose';
import { ChatService } from './chat.service';
import { Message } from './schemas/message.schema';
import { ChatRoomReadState } from './schemas/chat-room-read-state.schema';
import { ChatRoom } from './schemas/chat-room.schema';
import { UsersService } from '../users/users.service';
import { TeamsService } from '../users/teams.service';
import { GameIdentitiesService } from '../users/game-identities.service';
import { StickerAccessService } from '../users/sticker-access.service';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatAttachmentsService } from './chat-attachments.service';

describe('ChatService.getPeerReadUptoMessageId', () => {
  const userId = '507f1f77bcf86cd799439011';
  const roomId = '507f1f77bcf86cd799439012';
  const peerLow = '507f1f77bcf86cd799439021';
  const peerHigh = '507f1f77bcf86cd799439099';

  const chatReadStateModel = {
    find: jest.fn(),
  };

  const chatServiceDeps = [
    { provide: getModelToken(Message.name), useValue: {} },
    { provide: getModelToken(ChatRoomReadState.name), useValue: chatReadStateModel },
    { provide: getModelToken(ChatRoom.name), useValue: {} },
    { provide: UsersService, useValue: {} },
    { provide: TeamsService, useValue: {} },
    { provide: GameIdentitiesService, useValue: {} },
    { provide: StickerAccessService, useValue: {} },
    { provide: ChatRoomsService, useValue: {} },
    { provide: ChatAttachmentsService, useValue: {} },
  ];

  let service: ChatService;

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [ChatService, ...chatServiceDeps],
    }).compile();
    service = moduleRef.get(ChatService);
    jest.spyOn(service as any, 'assertUserMayUseChat').mockResolvedValue(undefined);
    jest.spyOn(service as any, 'assertRoomForUser').mockResolvedValue({
      roomObjectId: new Types.ObjectId(roomId),
    });
  });

  it('returns max peer lastReadMessageId excluding self', async () => {
    chatReadStateModel.find.mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([
            { lastReadMessageId: peerLow },
            { lastReadMessageId: peerHigh },
          ]),
        }),
      }),
    });

    await expect(service.getPeerReadUptoMessageId(userId, roomId)).resolves.toBe(
      peerHigh,
    );
    expect(chatReadStateModel.find).toHaveBeenCalledWith({
      roomId: new Types.ObjectId(roomId),
      userId: { $ne: userId },
    });
  });

  it('rejects invalid room id', async () => {
    await expect(
      service.getPeerReadUptoMessageId(userId, 'bad-id'),
    ).rejects.toBeInstanceOf(BadRequestException);
  });
});
