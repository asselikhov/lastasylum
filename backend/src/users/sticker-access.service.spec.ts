import { Test, TestingModule } from '@nestjs/testing';
import { getModelToken } from '@nestjs/mongoose';
import { Types } from 'mongoose';
import { StickerAccessService } from './sticker-access.service';
import { AllianceStickerRoleGrant } from './schemas/alliance-sticker-role-grant.schema';
import { AllianceStickerUserGrant } from './schemas/alliance-sticker-user-grant.schema';
import { User } from './schemas/user.schema';
import { GameIdentitiesService } from './game-identities.service';

describe('StickerAccessService', () => {
  let service: StickerAccessService;
  const roleGrantModel = {
    find: jest.fn(),
    deleteMany: jest.fn(),
    insertMany: jest.fn(),
    updateMany: jest.fn(),
  };
  const userGrantModel = {
    find: jest.fn(),
    deleteMany: jest.fn(),
    insertMany: jest.fn(),
  };
  const userModel = {
    find: jest.fn(),
    findById: jest.fn(),
  };
  const gameIdentities = {
    getActiveIdentity: jest.fn(),
  };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module: TestingModule = await Test.createTestingModule({
      providers: [
        StickerAccessService,
        { provide: getModelToken(AllianceStickerRoleGrant.name), useValue: roleGrantModel },
        { provide: getModelToken(AllianceStickerUserGrant.name), useValue: userGrantModel },
        { provide: getModelToken(User.name), useValue: userModel },
        { provide: GameIdentitiesService, useValue: gameIdentities },
      ],
    }).compile();
    service = module.get(StickerAccessService);
  });

  it('replaceUserPackGrants updates only target user grants', async () => {
    const alliance = 'TestAlliance';
    const userId = new Types.ObjectId().toString();
    userModel.findById.mockReturnValue({
      exec: jest.fn().mockResolvedValue({
        _id: new Types.ObjectId(userId),
        allianceName: alliance,
        username: 'u1',
        role: 'MEMBER',
        gameIdentities: [],
      }),
    });
    userGrantModel.deleteMany.mockReturnValue({ exec: jest.fn().mockResolvedValue({}) });
    userGrantModel.insertMany.mockResolvedValue([]);
    roleGrantModel.find.mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([]),
      }),
    });
    userGrantModel.find.mockReturnValue({
      lean: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([
          { packKey: 'zlobyaka', userId: new Types.ObjectId(userId) },
        ]),
      }),
    });
    userModel.find.mockReturnValue({
      sort: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue([]),
      }),
    });

    const view = await service.replaceUserPackGrants(alliance, userId, ['zlobyaka']);

    expect(userGrantModel.deleteMany).toHaveBeenCalledWith({
      allianceName: alliance,
      userId: new Types.ObjectId(userId),
    });
    expect(roleGrantModel.deleteMany).not.toHaveBeenCalled();
    expect(view.userGrants.zlobyaka).toEqual([userId]);
  });
});
