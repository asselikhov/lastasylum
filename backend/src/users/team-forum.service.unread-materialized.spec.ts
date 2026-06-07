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

describe('TeamForumService materialized unread', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';
  const topicId = new Types.ObjectId('507f1f77bcf86cd799439021');
  const lastMsgId = new Types.ObjectId('507f1f77bcf86cd799439030');

  const topicDoc = {
    _id: topicId,
    lastMessageId: lastMsgId,
    lastMessageSenderUserId: 'other-user',
  };

  const topicReadStateModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([
            {
              topicId,
              lastReadMessageId: '507f1f77bcf86cd799439020',
              unreadCount: 3,
            },
          ]),
        }),
      }),
    }),
    collection: { name: 'teamforumtopicreadstates' },
  };

  let service: TeamForumService;

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamForumService,
        { provide: getModelToken(TeamForumTopic.name), useValue: {} },
        { provide: getModelToken(TeamForumMessage.name), useValue: { aggregate: jest.fn() } },
        {
          provide: getModelToken(TeamForumTopicReadState.name),
          useValue: topicReadStateModel,
        },
        { provide: getModelToken(User.name), useValue: {} },
        {
          provide: TeamsService,
          useValue: { getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({}) },
        },
        { provide: TeamNewsAttachmentsService, useValue: {} },
        { provide: StickerAccessService, useValue: {} },
        { provide: GameIdentitiesService, useValue: {} },
        { provide: UsersService, useValue: {} },
        { provide: PinAuditService, useValue: {} },
      ],
    }).compile();
    service = moduleRef.get(TeamForumService);
  });

  it('uses materialized unreadCount without heavy aggregate', async () => {
    const unread = await service['countUnreadForumMessages'](
      new Types.ObjectId(teamId),
      userId,
      [topicId],
      [topicDoc as never],
    );
    expect(unread.get(topicId.toString())).toBe(3);
  });
});
