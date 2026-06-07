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

describe('ChatGateway.afterMessageCreated', () => {
  let gateway: ChatGateway;
  const emit = jest.fn();
  const to = jest.fn().mockReturnValue({ emit });
  const broadcastNewMessage = jest.fn();
  const fanOutNewMessageToEligibleOverlayClients = jest
    .fn()
    .mockResolvedValue(undefined);
  const notifyRoomUnreadAfterNewMessage = jest.fn().mockResolvedValue(undefined);
  const listActiveUserIdsForChatRoomAccess = jest
    .fn()
    .mockResolvedValue(['teammate1']);
  const findById = jest.fn().mockResolvedValue({
    title: ALLIANCE_RAID_ROOM_TITLE,
    allianceId: 'pt:team1',
  });

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        ChatGateway,
        { provide: ChatService, useValue: {} },
        {
          provide: ChatRoomsService,
          useValue: { findById },
        },
        {
          provide: UsersService,
          useValue: {
            listActiveUserIdsForChatRoomAccess,
            listOverlayIngameTeammateIds: jest.fn().mockResolvedValue([]),
            resolveTeamDisplayNameForGameEventPush: jest
              .fn()
              .mockResolvedValue('Team'),
            findAvatarRelativeUrlsByIds: jest.fn().mockResolvedValue(new Map()),
          },
        },
        { provide: JwtService, useValue: {} },
        { provide: ConfigService, useValue: {} },
        {
          provide: PushNotificationsService,
          useValue: { notifyGameEventAlert: jest.fn().mockResolvedValue(undefined) },
        },
        { provide: ChatAttachmentsService, useValue: {} },
        { provide: OverlayReactionLogService, useValue: {} },
      ],
    }).compile();
    gateway = module.get(ChatGateway);
    gateway.server = {
      to,
      adapter: { rooms: new Map() },
      sockets: new Map(),
    } as never;
    jest
      .spyOn(gateway, 'broadcastNewMessage')
      .mockImplementation(broadcastNewMessage);
    jest
      .spyOn(gateway as unknown as { fanOutNewMessageToEligibleOverlayClients: typeof fanOutNewMessageToEligibleOverlayClients }, 'fanOutNewMessageToEligibleOverlayClients')
      .mockImplementation(fanOutNewMessageToEligibleOverlayClients);
    jest
      .spyOn(gateway, 'notifyRoomUnreadAfterNewMessage')
      .mockImplementation(notifyRoomUnreadAfterNewMessage);
  });

  it('broadcasts room message immediately for game-event quick commands', () => {
    const message = { _id: 'msg1', text: 'HQ excavation', roomId: 'raid-room' };
    gateway.afterMessageCreated({
      roomId: 'raid-room',
      message,
      senderUserId: 'sender1',
      gameEventId: 'hq_excavation',
      gameEventText: 'HQ excavation',
      messageAllianceId: 'pt:team1',
      messageId: 'msg1',
      senderName: 'Alice',
    });
    expect(broadcastNewMessage).toHaveBeenCalledWith('raid-room', message);
  });

  it('broadcasts room message immediately for coordinate quick commands', () => {
    const message = { _id: 'msg2', text: 'Штурм X:1 Y:2', roomId: 'raid-room' };
    gateway.afterMessageCreated({
      roomId: 'raid-room',
      message,
      senderUserId: 'sender1',
    });
    expect(broadcastNewMessage).toHaveBeenCalledWith('raid-room', message);
  });

  it('schedules overlay fanout and unread in background', async () => {
    gateway.afterMessageCreated({
      roomId: 'raid-room',
      message: { _id: 'msg3', text: 'ping', roomId: 'raid-room' },
      senderUserId: 'sender1',
    });
    expect(broadcastNewMessage).toHaveBeenCalled();
    await new Promise((r) => setImmediate(r));
    expect(fanOutNewMessageToEligibleOverlayClients).toHaveBeenCalled();
    expect(notifyRoomUnreadAfterNewMessage).toHaveBeenCalledWith(
      'raid-room',
      'sender1',
      ['teammate1'],
    );
  });
});
