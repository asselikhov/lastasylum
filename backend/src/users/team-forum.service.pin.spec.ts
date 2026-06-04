import { ForbiddenException } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { TeamForumService } from './team-forum.service';
import { TeamForumTopic } from './schemas/team-forum-topic.schema';
import { TeamForumMessage } from './schemas/team-forum-message.schema';
import { TeamForumTopicReadState } from './schemas/team-forum-topic-read-state.schema';
import { User } from './schemas/user.schema';
import { TeamsService } from './teams.service';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { StickerAccessService } from './sticker-access.service';
import { GameIdentitiesService } from './game-identities.service';
import { UsersService } from './users.service';
import { PlayerTeamMemberRole } from '../common/enums/player-team-member-role.enum';

describe('TeamForumService pin', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const topicId = '507f1f77bcf86cd799439012';
  const userId = '507f1f77bcf86cd799439013';
  const messageId = '507f1f77bcf86cd799439014';

  const topicDoc = {
    _id: new Types.ObjectId(topicId),
    teamId: new Types.ObjectId(teamId),
    title: 'General',
    createdByUserId: userId,
    messageCount: 1,
    pinnedMessageId: null,
    pinnedAt: null,
    pinnedByUserId: null,
    save: jest.fn().mockImplementation(function (this: typeof topicDoc) {
      return Promise.resolve(this);
    }),
  };

  const msgDoc = {
    _id: new Types.ObjectId(messageId),
    topicId: new Types.ObjectId(topicId),
    teamId: new Types.ObjectId(teamId),
    senderUserId: userId,
    senderUsername: 'u',
    text: 'pin me',
    deletedAt: null,
    imageFileId: null,
    imageFileIds: [],
    createdAt: new Date(),
    editedAt: null,
  };

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
    getSquadRoleForUser: jest.fn(),
  };

  const topicModel = {
    findOne: jest.fn().mockResolvedValue(topicDoc),
  };

  const messageModel = {
    findOne: jest.fn().mockResolvedValue(msgDoc),
    find: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([msgDoc]),
      }),
    }),
    deleteOne: jest.fn().mockResolvedValue({ deletedCount: 1 }),
  };

  let service: TeamForumService;

  beforeEach(async () => {
    jest.clearAllMocks();
    topicModel.findOne.mockResolvedValue(topicDoc);
    messageModel.findOne.mockResolvedValue(msgDoc);

    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamForumService,
        { provide: getModelToken(TeamForumTopic.name), useValue: topicModel },
        { provide: getModelToken(TeamForumMessage.name), useValue: messageModel },
        {
          provide: getModelToken(TeamForumTopicReadState.name),
          useValue: { collection: { name: 'read' } },
        },
        { provide: getModelToken(User.name), useValue: {} },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: {} },
        { provide: StickerAccessService, useValue: {} },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: UsersService, useValue: {} },
      ],
    }).compile();

    service = moduleRef.get(TeamForumService);
  });

  it('allows R4 to pin forum message', async () => {
    teams.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R4);
    const { pinChanged } = await service.setTopicPinnedMessage(
      teamId,
      topicId,
      userId,
      messageId,
    );
    expect(pinChanged.pinnedMessageId).toBe(messageId);
    expect(topicDoc.save).toHaveBeenCalled();
  });

  it('rejects R2 from pinning', async () => {
    teams.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R2);
    await expect(
      service.setTopicPinnedMessage(teamId, topicId, userId, messageId),
    ).rejects.toBeInstanceOf(ForbiddenException);
  });

  it('replaces pin when pinning another message', async () => {
    teams.getSquadRoleForUser.mockReturnValue(PlayerTeamMemberRole.R5);
    await service.setTopicPinnedMessage(teamId, topicId, userId, messageId);
    const otherId = '507f1f77bcf86cd799439099';
    messageModel.findOne.mockResolvedValue({
      ...msgDoc,
      _id: new Types.ObjectId(otherId),
    });
    const { pinChanged } = await service.setTopicPinnedMessage(
      teamId,
      topicId,
      userId,
      otherId,
    );
    expect(pinChanged.pinnedMessageId).toBe(otherId);
  });
});
