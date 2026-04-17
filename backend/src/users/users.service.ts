import { ConflictException, Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { User, UserDocument } from './schemas/user.schema';

export type SafeUser = {
  id: string;
  username: string;
  email: string;
  role: AllianceRole;
  allianceName: string;
  membershipStatus: TeamMembershipStatus;
};

@Injectable()
export class UsersService {
  constructor(
    @InjectModel(User.name) private readonly userModel: Model<User>,
  ) {}

  effectiveMembership(user: UserDocument): TeamMembershipStatus {
    return user.membershipStatus ?? TeamMembershipStatus.ACTIVE;
  }

  async findByEmail(email: string): Promise<UserDocument | null> {
    return this.userModel.findOne({ email: email.toLowerCase() }).exec();
  }

  async createUser(input: {
    username: string;
    email: string;
    passwordHash: string;
    role?: AllianceRole;
  }): Promise<UserDocument> {
    return this.userModel.create({
      username: input.username,
      email: input.email.toLowerCase(),
      passwordHash: input.passwordHash,
      role: input.role ?? AllianceRole.R2,
      membershipStatus: TeamMembershipStatus.PENDING,
    });
  }

  async findById(id: string): Promise<UserDocument | null> {
    return this.userModel.findById(id).exec();
  }

  async listByAlliance(allianceName: string): Promise<UserDocument[]> {
    return this.userModel
      .find({ allianceName })
      .sort({ membershipStatus: 1, role: 1, username: 1 })
      .exec();
  }

  async updateRole(
    userId: string,
    role: AllianceRole,
  ): Promise<UserDocument | null> {
    return this.userModel
      .findByIdAndUpdate(userId, { role }, { new: true })
      .exec();
  }

  async updateMembershipStatus(
    userId: string,
    status: TeamMembershipStatus,
  ): Promise<UserDocument | null> {
    const update: Partial<User> = { membershipStatus: status };
    if (status === TeamMembershipStatus.REMOVED) {
      update.refreshTokenHash = null;
    }
    return this.userModel
      .findByIdAndUpdate(userId, update, { new: true })
      .exec();
  }

  async updateUsername(
    userId: string,
    username: string,
  ): Promise<UserDocument | null> {
    const trimmed = username.trim();
    const taken = await this.userModel
      .findOne({
        username: trimmed,
        _id: { $ne: userId },
      })
      .exec();
    if (taken) {
      throw new ConflictException('Username is already taken');
    }
    return this.userModel
      .findByIdAndUpdate(userId, { username: trimmed }, { new: true })
      .exec();
  }

  async deleteUser(userId: string): Promise<boolean> {
    const res = await this.userModel.deleteOne({ _id: userId }).exec();
    return res.deletedCount === 1;
  }

  async updateRefreshTokenHash(
    userId: string,
    refreshTokenHash: string | null,
  ): Promise<void> {
    await this.userModel.findByIdAndUpdate(userId, { refreshTokenHash }).exec();
  }

  async muteUser(
    userId: string,
    mutedUntil: Date | null,
  ): Promise<UserDocument | null> {
    return this.userModel
      .findByIdAndUpdate(userId, { mutedUntil }, { new: true })
      .exec();
  }

  async setPasswordResetToken(
    email: string,
    tokenHash: string,
    expires: Date,
  ): Promise<void> {
    await this.userModel
      .updateOne(
        { email: email.toLowerCase() },
        {
          $set: {
            passwordResetTokenHash: tokenHash,
            passwordResetExpires: expires,
          },
        },
      )
      .exec();
  }

  async applyPasswordReset(
    email: string,
    newPasswordHash: string,
  ): Promise<UserDocument | null> {
    return this.userModel
      .findOneAndUpdate(
        { email: email.toLowerCase() },
        {
          $set: {
            passwordHash: newPasswordHash,
            refreshTokenHash: null,
          },
          $unset: {
            passwordResetTokenHash: 1,
            passwordResetExpires: 1,
          },
        },
        { new: true },
      )
      .exec();
  }

  toSafeUser(user: UserDocument): SafeUser {
    return {
      id: user._id.toString(),
      username: user.username,
      email: user.email,
      role: user.role,
      allianceName: user.allianceName,
      membershipStatus: this.effectiveMembership(user),
    };
  }
}
