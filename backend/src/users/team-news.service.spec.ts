import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import mongoose, { Types } from 'mongoose';
import { TeamNewsService } from './team-news.service';
import { TeamNews, TeamNewsSchema } from './schemas/team-news.schema';
import { User } from './schemas/user.schema';
import { TeamsService } from './teams.service';
import { TeamNewsAttachmentsService } from './team-news-attachments.service';

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

  const userModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([
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
        { provide: getModelToken(User.name), useValue: userModel },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: attachments },
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
