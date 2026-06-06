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
import { PlayerTeamMemberRole } from '../common/enums/player-team-member-role.enum';

describe('TeamForumService.postMessage idempotency', () => {
  const teamId = '507f1f77bcf86cd799439011';
  const topicId = '507f1f77bcf86cd799439021';
  const userId = '507f1f77bcf86cd799439012';
  const existingId = new Types.ObjectId();

  const topicDoc = {
    _id: new Types.ObjectId(topicId),
    teamId: new Types.ObjectId(teamId),
    messageCount: 1,
    save: jest.fn().mockResolvedValue(undefined),
  };

  const findOneTopic = jest.fn().mockResolvedValue(topicDoc);
  const findOneMessage = jest.fn();
  const findByIdMessage = jest.fn().mockResolvedValue(null);
  const create = jest.fn();
  const findByIdUser = jest.fn().mockResolvedValue({
    _id: userId,
    telegramUsername: '@alice',
  });

  const teams = {
    getTeamIfMemberOrThrow: jest.fn().mockResolvedValue({ _id: teamId }),
    resolveSquadRoleForMember: jest.fn().mockReturnValue(PlayerTeamMemberRole.R1),
  };

  let service: TeamForumService;

  beforeEach(async () => {
    jest.clearAllMocks();
    findOneTopic.mockResolvedValue(topicDoc);
    findOneMessage.mockReturnValue({ exec: jest.fn().mockResolvedValue(null) });
    const moduleRef = await Test.createTestingModule({
      providers: [
        TeamForumService,
        {
          provide: getModelToken(TeamForumTopic.name),
          useValue: { findOne: findOneTopic },
        },
        {
          provide: getModelToken(TeamForumMessage.name),
          useValue: {
            findOne: findOneMessage,
            findById: findByIdMessage,
            create,
          },
        },
        {
          provide: getModelToken(TeamForumTopicReadState.name),
          useValue: {},
        },
        {
          provide: getModelToken(User.name),
          useValue: { findById: jest.fn().mockReturnValue({ exec: findByIdUser }) },
        },
        { provide: TeamsService, useValue: teams },
        { provide: TeamNewsAttachmentsService, useValue: {} },
        {
          provide: StickerAccessService,
          useValue: {
            assertUserMaySendStickerMessage: jest.fn().mockResolvedValue(undefined),
          },
        },
        {
          provide: GameIdentitiesService,
          useValue: {
            ensureMigrated: jest.fn().mockImplementation((u) => u),
            resolveSenderUsername: jest.fn().mockReturnValue('Alice'),
            resolveSenderServerNumber: jest.fn().mockReturnValue(1),
          },
        },
        { provide: UsersService, useValue: {} },
        { provide: PinAuditService, useValue: {} },
      ],
    }).compile();
    service = moduleRef.get(TeamForumService);
  });

  it('returns existing message when clientMessageId repeats', async () => {
    const existingDoc = {
      _id: existingId,
      topicId: new Types.ObjectId(topicId),
      teamId: new Types.ObjectId(teamId),
      senderUserId: userId,
      senderUsername: 'Alice',
      senderRole: PlayerTeamMemberRole.R1,
      senderTeamTag: null,
      senderServerNumber: 1,
      text: 'hello',
      replyToMessageId: null,
      imageFileId: null,
      imageFileIds: [],
      fileFileId: null,
      deletedAt: null,
      clientMessageId: 'client-uuid-1',
      createdAt: new Date(),
      updatedAt: new Date(),
    };
    findOneMessage.mockReturnValue({
      exec: jest.fn().mockResolvedValue(existingDoc),
    });

    const result = await service.postMessage(
      teamId,
      topicId,
      userId,
      'hello',
      null,
      [],
      null,
      null,
      'client-uuid-1',
    );

    expect(create).not.toHaveBeenCalled();
    expect(result.id).toBe(existingId.toString());
    expect(result.clientMessageId).toBe('client-uuid-1');
  });
});
