import { Test } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { PinAuditService } from './pin-audit.service';
import { PinAuditLog } from './schemas/pin-audit-log.schema';

describe('PinAuditService', () => {
  const create = jest.fn();
  const find = jest.fn();
  let service: PinAuditService;

  beforeEach(async () => {
    jest.clearAllMocks();
    find.mockReturnValue({
      sort: jest.fn().mockReturnValue({
        limit: jest.fn().mockReturnValue({
          lean: jest.fn().mockReturnValue({
            exec: jest.fn().mockResolvedValue([
              {
                _id: new Types.ObjectId(),
                teamId: new Types.ObjectId(),
                scope: 'chat',
                scopeId: 'room1',
                messageId: 'msg1',
                action: 'pin',
                userId: 'user1',
                createdAt: new Date('2026-01-01T00:00:00Z'),
              },
            ]),
          }),
        }),
      }),
    });
    const moduleRef = await Test.createTestingModule({
      providers: [
        PinAuditService,
        {
          provide: getModelToken(PinAuditLog.name),
          useValue: { create, find },
        },
      ],
    }).compile();
    service = moduleRef.get(PinAuditService);
  });

  it('append writes audit row for valid team chat pin', async () => {
    const teamId = new Types.ObjectId().toHexString();
    await service.append({
      teamId,
      scope: 'chat',
      scopeId: 'room1',
      messageId: 'msg1',
      action: 'pin',
      userId: 'user1',
    });
    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({
        scope: 'chat',
        scopeId: 'room1',
        action: 'pin',
      }),
    );
  });

  it('listForTeam returns recent rows', async () => {
    const teamId = new Types.ObjectId().toHexString();
    const rows = await service.listForTeam(teamId, 10);
    expect(rows).toHaveLength(1);
    expect(rows[0].scope).toBe('chat');
  });
});
