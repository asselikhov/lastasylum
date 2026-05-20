import { Types } from 'mongoose';
import {
  attachmentUploaderIdEquals,
  squadMemberUserIdEquals,
} from './squad-member-id.util';

describe('squad-member-id.util', () => {
  const userId = '507f1f77bcf86cd799439011';

  it('matches ObjectId squad member ids', () => {
    expect(
      squadMemberUserIdEquals(new Types.ObjectId(userId), userId),
    ).toBe(true);
  });

  it('matches legacy string squad member ids', () => {
    expect(squadMemberUserIdEquals(userId, userId)).toBe(true);
  });

  it('matches attachment uploader stored as ObjectId', () => {
    expect(
      attachmentUploaderIdEquals(new Types.ObjectId(userId), userId),
    ).toBe(true);
  });
});
