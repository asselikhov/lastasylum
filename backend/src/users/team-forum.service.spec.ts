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
import { PinAuditService } from './pin-audit.service';

describe('TeamForumService.sumUnreadMessages', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';
  const topicA = new Types.ObjectId('507f1f77bcf86cd799439021');
  const topicB = new Types.ObjectId('507f1f77bcf86cd799439022');

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
  };

  const topicModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        sort: jest.fn().mockReturnValue({
          limit: jest.fn().mockReturnValue({
            lean: jest.fn().mockResolvedValue([
              { _id: topicA, lastMessageId: new Types.ObjectId() },
              { _id: topicB, lastMessageId: new Types.ObjectId() },
            ]),
          }),
        }),
      }),
    }),
  };

  const messageModel = {
    aggregate: jest.fn().mockResolvedValue([
      { _id: topicA, count: 3 },
      { _id: topicB, count: 2 },
    ]),
  };

  const topicReadStateModel = {
    collection: { name: 'teamforumtopicreadstates' },
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([]),
        }),
      }),
    }),
  };

  let service: TeamForumService;

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamForumService,
        { provide: getModelToken(TeamForumTopic.name), useValue: topicModel },
        { provide: getModelToken(TeamForumMessage.name), useValue: messageModel },
        {
          provide: getModelToken(TeamForumTopicReadState.name),
          useValue: topicReadStateModel,
        },
        { provide: getModelToken(User.name), useValue: {} },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: {} },
        { provide: StickerAccessService, useValue: {} },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: UsersService, useValue: {} },
        { provide: PinAuditService, useValue: { append: jest.fn() } },
      ],
    }).compile();
    service = moduleRef.get(TeamForumService);
  });

  it('sums unread counts without loading full topic rows', async () => {
    const total = await service.sumUnreadMessages(teamId, userId);
    expect(total).toBe(5);
    expect(topicModel.find).toHaveBeenCalledWith({
      teamId: new Types.ObjectId(teamId),
    });
    expect(messageModel.aggregate).toHaveBeenCalledTimes(1);
  });
});
