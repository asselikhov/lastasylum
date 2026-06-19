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
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';

describe('ChatService.createMessage idempotency', () => {
  let chatService: ChatService;
  const findOneLean = jest.fn();
  const create = jest.fn();
  const findByIdUser = jest.fn();

  beforeEach(async () => {
    jest.clearAllMocks();
    findByIdUser.mockResolvedValue({
      _id: 'u1',
      membershipStatus: TeamMembershipStatus.ACTIVE,
    });
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ChatService,
        {
          provide: getModelToken(Message.name),
          useValue: {
            findOne: jest.fn().mockReturnValue({ lean: () => ({ exec: findOneLean }) }),
            create,
          },
        },
        {
          provide: getModelToken(ChatRoomReadState.name),
          useValue: {},
        },
        {
          provide: getModelToken(ChatSystemMeta.name),
          useValue: {
            findOneAndUpdate: jest.fn().mockReturnValue({ exec: jest.fn() }),
            findOne: jest.fn().mockReturnValue({
              select: jest.fn().mockReturnValue({
                lean: jest.fn().mockReturnValue({ exec: jest.fn().mockResolvedValue(null) }),
              }),
            }),
          },
        },
        { provide: ChatAttachmentsService, useValue: {} },
        {
          provide: UsersService,
          useValue: {
            findById: findByIdUser,
            effectiveMembership: () => TeamMembershipStatus.ACTIVE,
          },
        },
        {
          provide: GameIdentitiesService,
          useValue: {
            resolveSenderUsername: jest.fn().mockReturnValue('Alice'),
            resolveSenderServerNumber: jest.fn().mockReturnValue(1),
          },
        },
        {
          provide: TeamsService,
          useValue: {
            resolveSquadRolesByUserIds: jest
              .fn()
              .mockResolvedValue(new Map([['u1', 'R1']])),
          },
        },
        {
          provide: ChatRoomsService,
          useValue: {
            findById: jest.fn().mockResolvedValue({
              _id: new Types.ObjectId(),
              allianceId: 'pt:team1',
              title: 'Raid',
            }),
          },
        },
        {
          provide: StickerAccessService,
          useValue: {
            assertUserMaySendStickerMessage: jest.fn().mockResolvedValue(undefined),
          },
        },
        { provide: PinAuditService, useValue: {} },
      ],
    }).compile();
    chatService = module.get(ChatService);
    jest.spyOn(chatService as any, 'assertRoomForUser').mockResolvedValue({
      allianceId: 'pt:team1',
      roomObjectId: new Types.ObjectId(),
    });
    jest.spyOn(chatService as any, 'getReplyTarget').mockResolvedValue(null);
    jest.spyOn(chatService as any, 'assertNotMuted').mockImplementation(() => undefined);
    jest
      .spyOn(chatService as any, 'viewMessageForUser')
      .mockImplementation((row: { _id: Types.ObjectId }) => ({
        _id: row._id.toString(),
        text: 'hello',
      }));
  });

  it('returns existing message when clientMessageId repeats', async () => {
    const existingId = new Types.ObjectId();
    findOneLean.mockResolvedValue({ _id: existingId, text: 'hello' });
    const result = await chatService.createMessage({
      text: 'hello',
      roomId: new Types.ObjectId().toString(),
      author: { userId: 'u1', role: 'MEMBER' },
      clientMessageId: 'client-uuid-1',
    });
    expect(create).not.toHaveBeenCalled();
    expect(result.message._id).toBe(existingId.toString());
    expect(result.created).toBe(false);
  });

  it('returns existing message when create races on duplicate clientMessageId', async () => {
    const existingId = new Types.ObjectId();
    findOneLean
      .mockResolvedValueOnce(null)
      .mockResolvedValueOnce({ _id: existingId, text: 'hello' });
    create.mockRejectedValue(
      Object.assign(new Error('duplicate key'), { code: 11000 }),
    );
    const result = await chatService.createMessage({
      text: 'hello',
      roomId: new Types.ObjectId().toString(),
      author: { userId: 'u1', role: 'MEMBER' },
      clientMessageId: 'client-uuid-race',
    });
    expect(create).toHaveBeenCalledTimes(1);
    expect(findOneLean).toHaveBeenCalledTimes(2);
    expect(result.message._id).toBe(existingId.toString());
    expect(result.created).toBe(false);
  });
});
