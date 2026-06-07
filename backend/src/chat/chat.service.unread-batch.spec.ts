import { getModelToken } from '@nestjs/mongoose';
import { Test, TestingModule } from '@nestjs/testing';
import { Types } from 'mongoose';
import { ChatService } from './chat.service';
import { Message } from './schemas/message.schema';
import { ChatRoomReadState } from './schemas/chat-room-read-state.schema';
import { ChatSystemMeta } from './schemas/chat-system-meta.schema';
import { UsersService } from '../users/users.service';
import { TeamsService } from '../users/teams.service';
import { ChatRoomsService } from './chat-rooms.service';
import { StickerAccessService } from '../users/sticker-access.service';
import { GameIdentitiesService } from '../users/game-identities.service';
import { ChatAttachmentsService } from './chat-attachments.service';
import { PinAuditService } from '../users/pin-audit.service';

describe('ChatService.countUnreadInRoomForUsers', () => {
  let chatService: ChatService;
  const findLean = jest.fn();

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ChatService,
        {
          provide: getModelToken(Message.name),
          useValue: {
            find: jest.fn().mockReturnValue({
              select: () => ({
                lean: () => ({ exec: findLean }),
              }),
            }),
          },
        },
        { provide: getModelToken(ChatRoomReadState.name), useValue: {} },
        {
          provide: getModelToken(ChatSystemMeta.name),
          useValue: {
            findOne: jest.fn().mockReturnValue({
              select: jest.fn().mockReturnValue({
                lean: jest.fn().mockReturnValue({ exec: jest.fn() }),
              }),
            }),
          },
        },
        { provide: ChatAttachmentsService, useValue: {} },
        { provide: UsersService, useValue: {} },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: TeamsService, useValue: {} },
        { provide: ChatRoomsService, useValue: {} },
        { provide: StickerAccessService, useValue: {} },
        { provide: PinAuditService, useValue: {} },
      ],
    }).compile();
    chatService = module.get(ChatService);
  });

  it('counts unread per user from one message scan', async () => {
    const roomId = new Types.ObjectId().toString();
    const m1 = new Types.ObjectId();
    const m2 = new Types.ObjectId();
    const readOid = new Types.ObjectId();
    findLean.mockResolvedValue([
      { _id: m1, senderId: 'alice' },
      { _id: m2, senderId: 'bob' },
    ]);
    const readByUser = new Map([
      [
        'carol',
        { lastReadMessageId: readOid.toString(), hiddenBeforeMessageId: null },
      ],
      ['bob', { lastReadMessageId: null, hiddenBeforeMessageId: null }],
    ]);
    const counts = await chatService.countUnreadInRoomForUsers(
      roomId,
      ['alice', 'bob', 'carol'],
      readByUser,
    );
    expect(counts.get('alice')).toBe(1);
    expect(counts.get('bob')).toBe(1);
    expect(counts.get('carol')).toBe(m2 > readOid ? 1 : 0);
  });
});
