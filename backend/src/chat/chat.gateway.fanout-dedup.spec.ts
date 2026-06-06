import { Test, TestingModule } from '@nestjs/testing';
import { JwtService } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { ChatGateway } from './chat.gateway';
import { ChatService } from './chat.service';
import { ChatRoomsService } from './chat-rooms.service';
import { UsersService } from '../users/users.service';
import { PushNotificationsService } from '../push/push-notifications.service';
import { ChatAttachmentsService } from './chat-attachments.service';
import { OverlayReactionLogService } from './overlay-reaction-log.service';
import { ALLIANCE_RAID_ROOM_TITLE } from '../common/constants/chat-room-constants';

describe('ChatGateway fanout dedup', () => {
  let gateway: ChatGateway;
  const emit = jest.fn();
  const to = jest.fn().mockReturnValue({ emit });
  const listSquadTeammateUserIdsForRaidFanout = jest
    .fn()
    .mockResolvedValue(['t1', 't2', 't3']);

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ChatGateway,
        { provide: ChatService, useValue: {} },
        {
          provide: ChatRoomsService,
          useValue: {
            findById: jest.fn().mockResolvedValue({
              title: ALLIANCE_RAID_ROOM_TITLE,
              allianceId: 'pt:team1',
            }),
          },
        },
        {
          provide: UsersService,
          useValue: {
            listActiveUserIdsForChatRoomAccess: jest
              .fn()
              .mockResolvedValue(['t1', 't2', 't3']),
            listSquadTeammateUserIdsForRaidFanout,
            resolveTeamDisplayNameForGameEventPush: jest
              .fn()
              .mockResolvedValue('Team'),
            findTelegramUsernamesByIds: jest.fn().mockResolvedValue(new Map()),
          },
        },
        { provide: JwtService, useValue: {} },
        { provide: ConfigService, useValue: {} },
        {
          provide: PushNotificationsService,
          useValue: {},
        },
        { provide: ChatAttachmentsService, useValue: {} },
        { provide: OverlayReactionLogService, useValue: {} },
      ],
    }).compile();
    gateway = module.get(ChatGateway);
    gateway.server = {
      to,
      adapter: {
        rooms: new Map([
          [
            'chat:raid-room',
            new Set(['socket-in-room']),
          ],
        ]),
      },
      sockets: new Map([
        [
          'socket-in-room',
          { data: { user: { userId: 't1' } } },
        ],
      ]),
    } as never;
  });

  it('does not duplicate raid fanout for users already in chat room or personal fanout', async () => {
    const message = { _id: 'msg1', roomId: 'raid-room' };
    gateway.broadcastNewMessageWithOverlayFanout(
      'raid-room',
      message,
      'sender1',
    );
    await new Promise((r) => setTimeout(r, 20));
    const userEmits = emit.mock.calls.filter(
      (call) => call[0] === 'message:new',
    );
    const t1Count = userEmits.filter((_, idx) =>
      to.mock.calls[idx]?.[0]?.includes('t1'),
    ).length;
    expect(t1Count).toBe(0);
    expect(userEmits.length).toBeGreaterThan(0);
  });
});
