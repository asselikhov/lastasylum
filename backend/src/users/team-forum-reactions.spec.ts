import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { NotFoundException } from '@nestjs/common';
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

describe('TeamForumService.toggleMessageReaction', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const topicId = '507f1f77bcf86cd799439021';
  const userId = '507f1f77bcf86cd799439012';
  const messageId = '507f1f77bcf86cd799439031';

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
  };

  const usersService = {
    findAvatarRelativeUrlsByIds: jest.fn().mockResolvedValue(new Map()),
  };

  let savedReactions: { emoji: string; userIds: string[] }[] = [];
  const messageDoc = {
    _id: new Types.ObjectId(messageId),
    teamId: new Types.ObjectId(teamId),
    topicId: new Types.ObjectId(topicId),
    senderUserId: 'other-user',
    senderUsername: 'bob',
    senderRole: PlayerTeamMemberRole.R1,
    senderTeamTag: null,
    senderServerNumber: null,
    text: 'hello',
    replyToMessageId: null,
    deletedAt: null,
    reactions: savedReactions,
    save: jest.fn().mockImplementation(function save(this: typeof messageDoc) {
      return Promise.resolve(this);
    }),
  };

  const messageModel = {
    findOne: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue(messageDoc),
    }),
    findById: jest.fn().mockResolvedValue(null),
  };

  let service: TeamForumService;

  beforeEach(async () => {
    savedReactions = [];
    messageDoc.reactions = savedReactions;
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamForumService,
        { provide: getModelToken(TeamForumTopic.name), useValue: {} },
        { provide: getModelToken(TeamForumMessage.name), useValue: messageModel },
        {
          provide: getModelToken(TeamForumTopicReadState.name),
          useValue: { collection: { name: 'teamforumtopicreadstates' } },
        },
        { provide: getModelToken(User.name), useValue: {} },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: {} },
        { provide: StickerAccessService, useValue: {} },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: UsersService, useValue: usersService },
      ],
    }).compile();
    service = moduleRef.get(TeamForumService);
  });

  it('adds emoji reaction for viewer', async () => {
    const row = await service.toggleMessageReaction(
      teamId,
      topicId,
      userId,
      messageId,
      '👍',
    );
    expect(messageDoc.reactions).toEqual([{ emoji: '👍', userIds: [userId] }]);
    expect(row.reactions).toEqual([
      { emoji: '👍', count: 1, reactedByMe: true },
    ]);
  });

  it('removes emoji reaction when toggled twice', async () => {
    savedReactions.push({ emoji: '👍', userIds: [userId] });
    const row = await service.toggleMessageReaction(
      teamId,
      topicId,
      userId,
      messageId,
      '👍',
    );
    expect(messageDoc.reactions).toEqual([]);
    expect(row.reactions).toEqual([]);
  });

  it('throws when message missing', async () => {
    messageModel.findOne.mockReturnValueOnce({
      exec: jest.fn().mockResolvedValue(null),
    });
    await expect(
      service.toggleMessageReaction(
        teamId,
        topicId,
        userId,
        messageId,
        '👍',
      ),
    ).rejects.toBeInstanceOf(NotFoundException);
  });
});
