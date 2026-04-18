import { Test } from '@nestjs/testing';
import { BadRequestException } from '@nestjs/common';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { ChatService } from './chat.service';
import { Message } from './schemas/message.schema';
import { UsersService } from '../users/users.service';
import { ChatRoomsService } from './chat-rooms.service';

describe('ChatService', () => {
  const findByIdUser = jest.fn();
  const findByIdRoom = jest.fn();
  const exec = jest.fn();

  let chatService: ChatService;
  let findMock: jest.Mock;

  beforeEach(async () => {
    jest.clearAllMocks();
    exec.mockResolvedValue([]);

    const chain: {
      sort: jest.Mock;
      limit: jest.Mock;
      lean: jest.Mock;
      exec: jest.Mock;
    } = {
      sort: jest.fn(),
      limit: jest.fn(),
      lean: jest.fn(),
      exec,
    };
    chain.sort.mockReturnValue(chain);
    chain.limit.mockReturnValue(chain);
    chain.lean.mockReturnValue(chain);

    findMock = jest.fn().mockReturnValue(chain);

    const moduleRef = await Test.createTestingModule({
      providers: [
        ChatService,
        { provide: getModelToken(Message.name), useValue: { find: findMock } },
        { provide: UsersService, useValue: { findById: findByIdUser } },
        { provide: ChatRoomsService, useValue: { findById: findByIdRoom } },
      ],
    }).compile();

    chatService = moduleRef.get(ChatService);
  });

  describe('searchMessages', () => {
    it('throws when q is empty', async () => {
      await expect(
        chatService.searchMessages('u1', new Types.ObjectId().toHexString(), '   '),
      ).rejects.toBeInstanceOf(BadRequestException);
      expect(findMock).not.toHaveBeenCalled();
    });

    it('throws when q is too long', async () => {
      await expect(
        chatService.searchMessages(
          'u1',
          new Types.ObjectId().toHexString(),
          'a'.repeat(121),
        ),
      ).rejects.toBeInstanceOf(BadRequestException);
    });

    it('queries with case-insensitive escaped regex and clamps limit', async () => {
      const roomOid = new Types.ObjectId();
      findByIdUser.mockResolvedValue({ allianceName: 'ally1' });
      findByIdRoom.mockResolvedValue({
        _id: roomOid,
        allianceId: 'ally1',
        archivedAt: null,
      });
      exec.mockResolvedValue([{ text: 'Hello' }]);

      const roomHex = roomOid.toHexString();
      const out = await chatService.searchMessages('u1', roomHex, 'a+b', {
        limit: 999,
      });

      expect(out).toEqual([
        expect.objectContaining({
          text: 'Hello',
          replyTo: null,
          replyToMessageId: null,
          deletedAt: null,
          deletedByUserId: null,
        }),
      ]);
      expect(findMock).toHaveBeenCalledTimes(1);
      const filter = findMock.mock.calls[0][0] as { text: RegExp; deletedAt: null };
      expect(filter.text.flags).toContain('i');
      expect(filter.text.test('xa+by')).toBe(true);
      expect(filter.text.test('xaby')).toBe(false);
      expect(filter.deletedAt).toBeNull();

      const chain = findMock.mock.results[0].value as { limit: jest.Mock };
      expect(chain.limit).toHaveBeenCalledWith(50);
    });

    it('uses minimum limit of 1', async () => {
      const roomOid = new Types.ObjectId();
      findByIdUser.mockResolvedValue({ allianceName: 'ally1' });
      findByIdRoom.mockResolvedValue({
        _id: roomOid,
        allianceId: 'ally1',
        archivedAt: null,
      });

      await chatService.searchMessages('u1', roomOid.toHexString(), 'x', {
        limit: 0,
      });

      const chain = findMock.mock.results[0].value as { limit: jest.Mock };
      expect(chain.limit).toHaveBeenCalledWith(1);
    });
  });
});
