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

describe('TeamForumService listTopics performance', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';
  const topicId = '507f1f77bcf86cd799439013';
  const messageId = '507f1f77bcf86cd799439014';

  const topicRow = {
    _id: new Types.ObjectId(topicId),
    teamId: new Types.ObjectId(teamId),
    title: 'General',
    createdByUserId: userId,
    messageCount: 5,
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
    lastMessageAt: new Date(),
    createdAt: new Date(),
    updatedAt: new Date(),
  };

  const msgDoc = {
    _id: new Types.ObjectId(messageId),
    topicId: new Types.ObjectId(topicId),
    teamId: new Types.ObjectId(teamId),
    senderUserId: userId,
    senderUsername: 'u',
    text: 'hello',
    deletedAt: null,
    imageFileId: null,
    imageFileIds: [],
    createdAt: new Date(),
    editedAt: null,
  };

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
  };

  const usersService = {
    findById: jest.fn(),
    findTelegramUsernamesByIds: jest.fn().mockResolvedValue(new Map()),
  };

  const topicModel = {
    find: jest.fn().mockReturnValue({
      sort: jest.fn().mockReturnValue({
        limit: jest.fn().mockReturnValue({
          lean: jest.fn().mockResolvedValue([topicRow]),
        }),
      }),
    }),
  };

  const messageFindMock = jest.fn().mockReturnValue({
    lean: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue([msgDoc]),
    }),
  });

  const messageModel = {
    find: messageFindMock,
    aggregate: jest.fn().mockResolvedValue([
      { _id: new Types.ObjectId(topicId), count: 5 },
    ]),
  };

  let service: TeamForumService;

  beforeEach(async () => {
    jest.clearAllMocks();
    messageFindMock.mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([msgDoc]),
      }),
    });

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
        { provide: UsersService, useValue: usersService },
      ],
    }).compile();

    service = moduleRef.get(TeamForumService);
    jest.spyOn(service as never, 'countUnreadForumMessages' as never).mockResolvedValue(new Map() as never);
    jest.spyOn(service as never, 'getLastReadMessageIdsByTopicIds' as never).mockResolvedValue(new Map() as never);
    jest.spyOn(service as never, 'lastMessageSendersByTopicIds' as never).mockResolvedValue(new Map() as never);
    jest.spyOn(service as never, 'enrichTopicsWithTelegram' as never).mockImplementation((rows) => rows as never);
  });

  it('list view=list omits pinnedMessages and skips full-team count aggregation', async () => {
    const rows = await service.listTopics(teamId, userId, { view: 'list' });
    expect(rows).toHaveLength(1);
    expect(rows[0].pinnedMessage?.text).toBe('hello');
    expect(rows[0].pinnedMessages).toEqual([]);
    expect(messageModel.aggregate).not.toHaveBeenCalled();
    expect(messageFindMock).toHaveBeenCalledTimes(1);
  });

  it('list view=full includes pinnedMessages history', async () => {
    const rows = await service.listTopics(teamId, userId, { view: 'full' });
    expect(rows[0].pinnedMessages).toHaveLength(1);
    expect(rows[0].pinnedMessages[0]?.text).toBe('hello');
    expect(messageModel.aggregate).toHaveBeenCalled();
  });
});
