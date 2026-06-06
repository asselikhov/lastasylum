import { Types } from 'mongoose';
import { UserAvatarService } from './user-avatar.service';

describe('UserAvatarService', () => {
  const userId = new Types.ObjectId().toString();

  function createService(opts?: {
    avatarKey?: string | null;
    deleteObject?: jest.Mock;
    putObject?: jest.Mock;
  }) {
    const save = jest.fn().mockResolvedValue(undefined);
    const user = {
      _id: userId,
      avatarKey: opts?.avatarKey ?? null,
      avatarUpdatedAt: opts?.avatarKey ? new Date('2026-01-01T00:00:00.000Z') : null,
      save,
    };
    const userModel = {
      findById: jest.fn().mockReturnValue({
        exec: jest.fn().mockResolvedValue(user),
      }),
    };
    const r2 = {
      putObject: opts?.putObject ?? jest.fn().mockResolvedValue(undefined),
      deleteObject: opts?.deleteObject ?? jest.fn().mockResolvedValue(undefined),
      getObjectStream: jest.fn(),
    };
    const service = new UserAvatarService(userModel as never, r2 as never);
    return { service, user, save, r2 };
  }

  it('upload stores new key and deletes previous avatar from R2', async () => {
    const deleteObject = jest.fn().mockResolvedValue(undefined);
    const { service, user, save, r2 } = createService({
      avatarKey: 'profiles/old/1.jpg',
      deleteObject,
    });

    const result = await service.upload({
      userId,
      buffer: Buffer.from([0xff, 0xd8, 0xff]),
      mimeType: 'image/jpeg',
      size: 3,
    });

    expect(r2.putObject).toHaveBeenCalled();
    expect(user.avatarKey).toMatch(/^profiles\//);
    expect(user.avatarUpdatedAt).toBeInstanceOf(Date);
    expect(save).toHaveBeenCalled();
    expect(deleteObject).toHaveBeenCalledWith('profiles/old/1.jpg');
    expect(result.avatarRelativeUrl).toContain(`/users/avatars/${userId}`);
  });

  it('delete clears user fields and removes R2 object', async () => {
    const deleteObject = jest.fn().mockResolvedValue(undefined);
    const { service, user, save } = createService({
      avatarKey: 'profiles/u/2.png',
      deleteObject,
    });

    await service.delete(userId);

    expect(user.avatarKey).toBeNull();
    expect(user.avatarUpdatedAt).toBeNull();
    expect(save).toHaveBeenCalled();
    expect(deleteObject).toHaveBeenCalledWith('profiles/u/2.png');
  });

  it('rejects empty upload', async () => {
    const { service } = createService();
    await expect(
      service.upload({
        userId,
        buffer: Buffer.alloc(0),
        mimeType: 'image/jpeg',
        size: 0,
      }),
    ).rejects.toThrow('file is empty');
  });
});
