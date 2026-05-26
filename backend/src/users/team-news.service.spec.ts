import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import mongoose, { Types } from 'mongoose';
import { TeamNewsService } from './team-news.service';
import { TeamNews, TeamNewsSchema } from './schemas/team-news.schema';
import { TeamNewsReadState } from './schemas/team-news-read-state.schema';
import { User } from './schemas/user.schema';
import { TeamsService } from './teams.service';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';
import { GameIdentitiesService } from './game-identities.service';

function mockGameIdentities() {
  return {
    resolveMemberDisplayNickname: jest.fn(
      (user: {
        username: string;
        gameIdentities?: Array<{ gameNickname?: string }>;
      }) => user.gameIdentities?.[0]?.gameNickname?.trim() || user.username,
    ),
    resolveSenderUsername: jest.fn(),
  };
}

describe('TeamNewsService poll create', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({
      _id: teamId,
      members: [{ userId, teamRole: 'R4' }],
    }),
    getSquadRoleForUser: jest.fn().mockReturnValue('R4'),
  };

  const attachments = {
    assertTeamImageSlots: jest.fn().mockResolvedValue([]),
  };

  const createdId = '507f1f77bcf86cd799439099';
  const newsModel = {
    create: jest.fn().mockImplementation((doc) => ({
      ...doc,
      _id: { toString: () => createdId },
    })),
    findOne: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue({
          _id: { toString: () => createdId },
          teamId: { toString: () => teamId },
          authorUserId: userId,
          title: 'В'.repeat(240),
          body: '',
          imageAttachments: [],
          poll: {
            question: 'В'.repeat(240),
            options: [
              { id: 'a', text: 'One' },
              { id: 'b', text: 'Two' },
            ],
            votes: [],
          },
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      }),
    }),
  };

  const newsReadStateModel = {
    findOne: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue(null),
      }),
    }),
    updateOne: jest
      .fn()
      .mockReturnValue({ exec: jest.fn().mockResolvedValue({}) }),
  };

  const userModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest
            .fn()
            .mockResolvedValue([
              { _id: userId, username: 'tester', telegramUsername: null },
            ]),
        }),
      }),
    }),
  };

  let service: TeamNewsService;

  beforeEach(async () => {
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamNewsService,
        { provide: getModelToken(TeamNews.name), useValue: newsModel },
        {
          provide: getModelToken(TeamNewsReadState.name),
          useValue: newsReadStateModel,
        },
        { provide: getModelToken(User.name), useValue: userModel },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: attachments },
        {
          provide: GameIdentitiesService,
          useValue: mockGameIdentities(),
        },
      ],
    }).compile();
    service = moduleRef.get(TeamNewsService);
    jest.clearAllMocks();
  });

  it('stores poll-only title up to 500 chars without mongoose validation error', async () => {
    const longQ = 'В'.repeat(240);
    await service.create(teamId, userId, {
      poll: {
        question: longQ,
        optionTexts: ['Да', 'Нет'],
      },
    });
    expect(newsModel.create).toHaveBeenCalledWith(
      expect.objectContaining({
        title: longQ,
        body: '',
        poll: expect.objectContaining({ question: longQ }),
      }),
    );
  });

  it('mongoose schema allows empty body for poll-only posts', () => {
    const M =
      mongoose.models.TeamNewsSchemaPollOnlyTest ??
      mongoose.model(
        'TeamNewsSchemaPollOnlyTest',
        TeamNewsSchema,
        'team_news_schema_test',
      );
    const doc = new M({
      teamId: new Types.ObjectId(),
      authorUserId: userId,
      title: 'Вопрос',
      body: '',
      poll: {
        question: 'Вопрос',
        options: [
          { id: 'a', text: 'Да' },
          { id: 'b', text: 'Нет' },
        ],
        votes: [],
      },
    });
    expect(doc.validateSync()).toBeUndefined();
  });

  it('returns detail after poll-only create via getOne', async () => {
    const longQ = 'В'.repeat(240);
    const detail = await service.create(teamId, userId, {
      poll: {
        question: longQ,
        optionTexts: ['Да', 'Нет'],
      },
    });
    expect(detail.id).toBe(createdId);
    expect(detail.hasPoll).toBe(true);
    expect(detail.poll?.question).toBe(longQ);
    expect(detail.authorUsername).toBe('tester');
  });
});

describe('TeamNewsService poll update', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';
  const newsId = '507f1f77bcf86cd799439099';

  const existingPoll = {
    question: 'Старый вопрос?',
    options: [
      { id: 'opt-a', text: 'Да' },
      { id: 'opt-b', text: 'Нет' },
    ],
    votes: [
      { userId: '507f1f77bcf86cd799439013', optionId: 'opt-a' },
      { userId: '507f1f77bcf86cd799439014', optionId: 'opt-b' },
    ],
  };

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({
      _id: teamId,
      members: [{ userId, teamRole: 'R4' }],
    }),
    getSquadRoleForUser: jest.fn().mockReturnValue('R4'),
  };

  const attachments = {
    assertTeamImageSlots: jest.fn().mockResolvedValue([]),
  };

  const doc = {
    _id: new Types.ObjectId(newsId),
    teamId: new Types.ObjectId(teamId),
    authorUserId: userId,
    title: 'News',
    body: 'Body',
    imageAttachments: [],
    poll: { ...existingPoll, votes: [...existingPoll.votes] },
    save: jest.fn().mockResolvedValue(undefined),
  };

  const leanRow = () => ({
    _id: { toString: () => newsId },
    teamId: { toString: () => teamId },
    authorUserId: userId,
    title: doc.title,
    body: doc.body,
    imageAttachments: [],
    poll: doc.poll,
    createdAt: new Date(),
    updatedAt: new Date(),
  });

  const newsReadStateModel = {
    findOne: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue(null),
      }),
    }),
  };

  const newsModel = {
    findOne: jest.fn().mockReturnValue({
      exec: jest.fn().mockResolvedValue(doc),
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockImplementation(async () => leanRow()),
      }),
    }),
  };

  const userModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([
            { _id: userId, username: 'editor', telegramUsername: null },
            {
              _id: '507f1f77bcf86cd799439013',
              username: 'v1',
              telegramUsername: null,
            },
            {
              _id: '507f1f77bcf86cd799439014',
              username: 'v2',
              telegramUsername: null,
            },
          ]),
        }),
      }),
    }),
  };

  let service: TeamNewsService;

  beforeEach(async () => {
    doc.poll = {
      ...existingPoll,
      votes: [...existingPoll.votes],
    };
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamNewsService,
        { provide: getModelToken(TeamNews.name), useValue: newsModel },
        {
          provide: getModelToken(TeamNewsReadState.name),
          useValue: newsReadStateModel,
        },
        { provide: getModelToken(User.name), useValue: userModel },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: attachments },
        {
          provide: GameIdentitiesService,
          useValue: mockGameIdentities(),
        },
      ],
    }).compile();
    service = moduleRef.get(TeamNewsService);
    jest.clearAllMocks();
  });

  it('preserves votes when poll question and option texts are edited', async () => {
    await service.update(teamId, newsId, userId, {
      poll: {
        question: 'Новый вопрос?',
        optionTexts: ['Да (уточнено)', 'Нет'],
        optionIds: ['opt-a', 'opt-b'],
      },
    });
    expect(doc.poll?.votes).toHaveLength(2);
    expect(doc.poll?.votes.map((v) => v.optionId).sort()).toEqual([
      'opt-a',
      'opt-b',
    ]);
    expect(doc.poll?.options[0]).toEqual({
      id: 'opt-a',
      text: 'Да (уточнено)',
    });
    expect(doc.save).toHaveBeenCalled();
  });
});

describe('TeamNewsService poll voter display names', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';
  const voterId = '507f1f77bcf86cd799439013';
  const newsId = '507f1f77bcf86cd799439099';

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({
      _id: teamId,
      members: [{ userId, teamRole: 'R4' }],
    }),
    getSquadRoleForUser: jest.fn().mockReturnValue('R4'),
  };

  const newsModel = {
    findOne: jest.fn().mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue({
          _id: { toString: () => newsId },
          teamId: { toString: () => teamId },
          authorUserId: userId,
          title: 'Poll',
          body: '',
          imageAttachments: [],
          poll: {
            question: 'Q?',
            options: [{ id: 'opt-a', text: 'Yes' }],
            votes: [{ userId: voterId, optionId: 'opt-a' }],
          },
          createdAt: new Date(),
          updatedAt: new Date(),
        }),
      }),
    }),
  };

  const userModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([
            {
              _id: userId,
              username: 'author@example.com',
              telegramUsername: null,
              gameIdentities: [{ gameNickname: 'AuthorNick' }],
            },
            {
              _id: voterId,
              username: 'fedko@example.com',
              telegramUsername: null,
              gameIdentities: [{ gameNickname: 'Fedko' }],
            },
          ]),
        }),
      }),
    }),
  };

  let service: TeamNewsService;

  beforeEach(async () => {
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamNewsService,
        { provide: getModelToken(TeamNews.name), useValue: newsModel },
        { provide: getModelToken(TeamNewsReadState.name), useValue: {} },
        { provide: getModelToken(User.name), useValue: userModel },
        { provide: TeamsService, useValue: teams },
        {
          provide: TeamNewsAttachmentsService,
          useValue: { assertTeamImageSlots: jest.fn() },
        },
        {
          provide: GameIdentitiesService,
          useValue: mockGameIdentities(),
        },
      ],
    }).compile();
    service = moduleRef.get(TeamNewsService);
  });

  it('getOne uses game nickname instead of email login for poll voters and author', async () => {
    const detail = await service.getOne(teamId, newsId, userId);
    expect(detail.authorUsername).toBe('AuthorNick');
    expect(detail.poll?.votes).toHaveLength(1);
    expect(detail.poll?.votes[0].username).toBe('Fedko');
  });
});
