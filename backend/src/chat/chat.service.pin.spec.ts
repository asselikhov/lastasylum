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

  const pinnedRoomAfterSet = () => ({
    ...roomDoc,
    pinnedMessageId: new Types.ObjectId(messageId),
    pinnedAt: new Date(),
    pinnedByUserId: userId,
    pinHistory: [
      {
        messageId: new Types.ObjectId(messageId),
        pinnedAt: new Date(),
        pinnedByUserId: userId,
      },
    ],
  });

  const chatRoomsService = {
    findById: jest.fn().mockResolvedValue(roomDoc),
    setPinnedMessage: jest
      .fn()
      .mockImplementation(async () => pinnedRoomAfterSet()),
    clearPinnedMessageIfMatches: jest.fn().mockResolvedValue(true),
    clearAllPinsEverywhere: jest.fn().mockResolvedValue(3),
    pinHistoryForRoom: jest.fn().mockImplementation((room: {
      pinHistory?: { messageId: Types.ObjectId }[];
      pinnedMessageId?: Types.ObjectId | null;
    }) => {
      if (room.pinHistory?.length) return room.pinHistory;
      if (room.pinnedMessageId) {
        return [
          {
            messageId: room.pinnedMessageId,
            pinnedAt: new Date(),
            pinnedByUserId: userId,
          },
        ];
      }
      return [];
    }),
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
    deleteMany: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue({ deletedCount: 10 }),
    }),
  };

  const chatReadStateModel = {
    deleteMany: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue({ deletedCount: 5 }),
    }),
  };

  const chatAttachments = {
    deleteAllMetadataForAdmin: jest.fn().mockResolvedValue(2),
  };

  const stickerAccess = {
    assertUserMaySendStickerMessage: jest.fn().mockResolvedValue(undefined),
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
          useValue: chatReadStateModel,
        },
        { provide: ChatAttachmentsService, useValue: chatAttachments },
        { provide: UsersService, useValue: usersService },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: TeamsService, useValue: teamsService },
        { provide: ChatRoomsService, useValue: chatRoomsService },
        { provide: StickerAccessService, useValue: stickerAccess },
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
    chatRoomsService.findById.mockResolvedValue({
      ...roomDoc,
      pinnedMessageId: null,
      pinnedAt: null,
      pinnedByUserId: null,
      pinHistory: [],
    });
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

  it('admin wipe clears all room pins', async () => {
    const result = await service.clearAllChatHistoryForAdmin();
    expect(chatRoomsService.clearAllPinsEverywhere).toHaveBeenCalled();
    expect(result.pinsCleared).toBe(3);
  });

  it('edit refreshes pin preview when edited message is pinned', async () => {
    teamsService.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R5);
    const pinnedRoom = pinnedRoomAfterSet();
    chatRoomsService.findById.mockResolvedValue(pinnedRoom);
    const savedLean = { ...messageLean, text: 'edited hello', editedAt: new Date() };
    const messageDoc = {
      ...messageLean,
      text: 'hello',
      editedAt: null,
      toObject: jest.fn().mockReturnValue(messageLean),
      save: jest.fn().mockImplementation(async function (this: typeof messageDoc) {
        this.text = 'edited hello';
        this.editedAt = new Date();
        this.toObject.mockReturnValue(savedLean);
        return this;
      }),
    };
    messageModel.findById.mockReturnValue({
      exec: jest.fn().mockResolvedValue(messageDoc),
    });
    messageModel.find.mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([savedLean]),
      }),
    });
    jest.spyOn(service, 'viewMessageForUser').mockResolvedValue({
      _id: messageId,
      text: 'edited hello',
    } as never);
    jest.spyOn(service, 'assertMayAccessMessageRoom').mockResolvedValue(undefined);
    jest.spyOn(service, 'assertMayModerateOthersMessage').mockResolvedValue(undefined);
    const { pinChanged } = await service.editMessage(userId, messageId, 'edited hello');
    expect(pinChanged).not.toBeNull();
    expect(pinChanged?.pinnedMessage?.text).toBe('edited hello');
  });
});
