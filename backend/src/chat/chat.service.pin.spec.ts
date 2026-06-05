import { ForbiddenException, NotFoundException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { ChatService } from './chat.service';
import { Message } from './schemas/message.schema';
import { ChatRoomReadState } from './schemas/chat-room-read-state.schema';
import { ChatRoomsService } from './chat-rooms.service';
import { ChatAttachmentsService } from './chat-attachments.service';
import { UsersService } from '../users/users.service';
import { GameIdentitiesService } from '../users/game-identities.service';
import { TeamsService } from '../users/teams.service';
import { StickerAccessService } from '../users/sticker-access.service';
import { PlayerTeamMemberRole } from '../common/enums/player-team-member-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { playerTeamChatAllianceId } from './chat-alliance-scope';

describe('ChatService pin (team rooms)', () => {
  const userId = '507f1f77bcf86cd799439011';
  const teamId = '507f1f77bcf86cd799439012';
  const roomId = '507f1f77bcf86cd799439013';
  const messageId = '507f1f77bcf86cd799439014';
  const ptScope = playerTeamChatAllianceId(teamId);

  const actor = {
    _id: new Types.ObjectId(userId),
    role: 'MEMBER',
    playerTeamId: new Types.ObjectId(teamId),
    username: 'officer',
    email: 'officer@test',
  };

  const roomDoc = {
    _id: new Types.ObjectId(roomId),
    allianceId: ptScope,
    archivedAt: null,
    pinnedMessageId: null,
    pinnedAt: null,
    pinnedByUserId: null,
    save: jest.fn(),
  };

  const messageLean = {
    _id: new Types.ObjectId(messageId),
    roomId: new Types.ObjectId(roomId),
    allianceId: ptScope,
    senderId: 'other',
    senderUsername: 'ally',
    text: 'hello',
    attachments: [],
    createdAt: new Date(),
    editedAt: null,
  };

  const usersService = {
    findById: jest.fn().mockResolvedValue({
      ...actor,
      effectiveMembership: () => TeamMembershipStatus.ACTIVE,
    }),
    effectiveMembership: jest.fn().mockReturnValue(TeamMembershipStatus.ACTIVE),
  };

  const chatRoomsService = {
    findById: jest.fn().mockResolvedValue(roomDoc),
    setPinnedMessage: jest.fn().mockImplementation(async () => ({
      ...roomDoc,
      pinnedMessageId: new Types.ObjectId(messageId),
      pinnedAt: new Date(),
      pinnedByUserId: userId,
    })),
    clearPinnedMessageIfMatches: jest.fn().mockResolvedValue(true),
  };

  const teamsService = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
    getSquadRoleForUser: jest.fn(),
  };

  const messageModel = {
    findById: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue(messageLean),
      }),
    }),
    find: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([messageLean]),
      }),
    }),
    deleteOne: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue({ deletedCount: 1 }),
    }),
    updateMany: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue({}),
    }),
  };

  let service: ChatService;

  beforeEach(async () => {
    jest.clearAllMocks();
    usersService.findById.mockResolvedValue({
      ...actor,
      allianceName: 'A',
      playerTeamId: new Types.ObjectId(teamId),
      gameIdentities: [],
    });
    chatRoomsService.findById.mockResolvedValue(roomDoc);

    const moduleRef = await Test.createTestingModule({
      providers: [
        ChatService,
        { provide: getModelToken(Message.name), useValue: messageModel },
        {
          provide: getModelToken(ChatRoomReadState.name),
          useValue: {},
        },
        { provide: ChatAttachmentsService, useValue: {} },
        { provide: UsersService, useValue: usersService },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: TeamsService, useValue: teamsService },
        { provide: ChatRoomsService, useValue: chatRoomsService },
        { provide: StickerAccessService, useValue: {} },
      ],
    }).compile();

    service = moduleRef.get(ChatService);
    jest.spyOn(service, 'assertUserMayUseChat').mockResolvedValue(undefined);
  });

  it('allows R5 to pin a message in pt:* room', async () => {
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R5);
    const { pinChanged } = await service.setRoomPinnedMessage(
      userId,
      roomId,
      messageId,
    );
    expect(pinChanged.pinnedMessageId).toBe(messageId);
    expect(pinChanged.pinnedMessage?.text).toBe('hello');
    expect(pinChanged.pinnedMessage?.imageThumbnailUrl).toBeNull();
    expect(pinChanged.pinnedMessage?.pinnedByUsername).toBe('officer');
    expect(chatRoomsService.setPinnedMessage).toHaveBeenCalled();
  });

  it('rejects R3 from pinning', async () => {
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R3);
    await expect(
      service.setRoomPinnedMessage(userId, roomId, messageId),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('rejects pin in non-team room scope', async () => {
    chatRoomsService.findById.mockResolvedValue({
      ...roomDoc,
      allianceId: 'global',
    });
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R5);
    await expect(
      service.setRoomPinnedMessage(userId, roomId, messageId),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('clears pin when pinned message is deleted', async () => {
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R5);
    messageModel.findById.mockReturnValue({
      exec: jest.fn().mockResolvedValue({
        ...messageLean,
        toObject: () => messageLean,
      }),
    });
    const result = await service.deleteMessage(userId, messageId);
    expect(result.pinChanged?.pinnedMessageId).toBeNull();
    expect(chatRoomsService.clearPinnedMessageIfMatches).toHaveBeenCalledWith(
      roomId,
      messageId,
    );
  });

  it('unpins with null messageId', async () => {
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R4);
    chatRoomsService.setPinnedMessage.mockResolvedValue({
      ...roomDoc,
      pinnedMessageId: null,
      pinnedAt: null,
      pinnedByUserId: null,
    });
    const { pinChanged } = await service.setRoomPinnedMessage(
      userId,
      roomId,
      null,
    );
    expect(pinChanged.pinnedMessage).toBeNull();
  });

  it('throws when message is not in room', async () => {
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R5);
    messageModel.findById.mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue({
          ...messageLean,
          roomId: new Types.ObjectId('507f1f77bcf86cd799439099'),
        }),
      }),
    });
    await expect(
      service.setRoomPinnedMessage(userId, roomId, messageId),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});
