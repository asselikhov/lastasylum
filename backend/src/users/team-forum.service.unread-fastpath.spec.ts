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

describe('TeamForumService unread fast-path', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const userId = '507f1f77bcf86cd799439012';
  const topicId = new Types.ObjectId('507f1f77bcf86cd799439021');
  const peerMsgId = new Types.ObjectId('507f1f77bcf86cd799439030');
  const ownMsgId = new Types.ObjectId('507f1f77bcf86cd799439031');

  const topicDoc = {
    _id: topicId,
    lastMessageId: ownMsgId,
    lastMessageSenderUserId: userId,
  };

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
  };

  const topicReadStateModel = {
    find: jest.fn().mockReturnValue({
      select: jest.fn().mockReturnValue({
        lean: jest.fn().mockReturnValue({
          exec: jest.fn().mockResolvedValue([
            { topicId, lastReadMessageId: peerMsgId.toString() },
          ]),
        }),
      }),
    }),
    collection: { name: 'teamforumtopicreadstates' },
  };

  const messageModel = {
    aggregate: jest.fn().mockResolvedValue([{ _id: topicId, count: 2 }]),
  };

  let service: TeamForumService;

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamForumService,
        { provide: getModelToken(TeamForumTopic.name), useValue: {} },
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
        { provide: PinAuditService, useValue: {} },
      ],
    }).compile();
    service = moduleRef.get(TeamForumService);
  });

  it('heavy-counts unread when last message is own but cursor is behind', async () => {
    const unread = await service['countUnreadForumMessages'](
      new Types.ObjectId(teamId),
      userId,
      [topicId],
      [topicDoc as never],
      new Map([[topicId.toString(), peerMsgId.toString()]]),
    );
    expect(unread.get(topicId.toString())).toBe(2);
    expect(messageModel.aggregate).toHaveBeenCalled();
  });
});
