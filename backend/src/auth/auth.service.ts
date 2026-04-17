import {
  ConflictException,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcrypt';
import type { StringValue } from 'ms';
import { LoginDto } from './dto/login.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { RegisterDto } from './dto/register.dto';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UserDocument } from '../users/schemas/user.schema';
import { UsersService } from '../users/users.service';

@Injectable()
export class AuthService {
  constructor(
    private readonly usersService: UsersService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  async register(dto: RegisterDto) {
    const existingUser = await this.usersService.findByEmail(dto.email);
    if (existingUser) {
      throw new ConflictException('Email is already in use');
    }

    const passwordHash = await bcrypt.hash(dto.password, 10);
    const user = await this.usersService.createUser({
      username: dto.username,
      email: dto.email,
      passwordHash,
      role: dto.role,
    });

    if (
      this.usersService.effectiveMembership(user) ===
      TeamMembershipStatus.PENDING
    ) {
      return {
        approvalRequired: true,
        user: this.usersService.toSafeUser(user),
      };
    }

    return this.signAuthResponse(
      user._id.toString(),
      user.email,
      user.username,
      user.role,
    );
  }

  async login(dto: LoginDto) {
    const user = await this.usersService.findByEmail(dto.email);
    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    const isPasswordValid = await bcrypt.compare(
      dto.password,
      user.passwordHash,
    );
    if (!isPasswordValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

    this.assertMembershipAllowsLogin(user);

    return this.signAuthResponse(
      user._id.toString(),
      user.email,
      user.username,
      user.role,
    );
  }

  async refresh(dto: RefreshTokenDto) {
    const refreshSecret =
      this.configService.getOrThrow<string>('JWT_REFRESH_SECRET');
    const payload = await this.jwtService
      .verifyAsync<{
        sub: string;
        email: string;
        username: string;
        role: string;
      }>(dto.refreshToken, { secret: refreshSecret })
      .catch(() => {
        throw new UnauthorizedException('Invalid refresh token');
      });

    const user = await this.usersService.findById(payload.sub);
    if (!user?.refreshTokenHash) {
      throw new UnauthorizedException('Refresh session is not active');
    }

    const isRefreshTokenValid = await bcrypt.compare(
      dto.refreshToken,
      user.refreshTokenHash,
    );
    if (!isRefreshTokenValid) {
      throw new UnauthorizedException('Invalid refresh token');
    }

    this.assertMembershipAllowsLogin(user);

    return this.signAuthResponse(
      user._id.toString(),
      user.email,
      user.username,
      user.role,
    );
  }

  private assertMembershipAllowsLogin(user: UserDocument): void {
    const status = this.usersService.effectiveMembership(user);
    if (status === TeamMembershipStatus.REMOVED) {
      throw new UnauthorizedException('Invalid credentials');
    }
    if (status === TeamMembershipStatus.PENDING) {
      throw new ForbiddenException(
        'Account pending administrator approval',
      );
    }
  }

  async logout(userId: string): Promise<{ success: true }> {
    await this.usersService.updateRefreshTokenHash(userId, null);
    return { success: true };
  }

  private async signAuthResponse(
    userId: string,
    email: string,
    username: string,
    role: string,
  ) {
    const accessToken = await this.jwtService.signAsync({
      sub: userId,
      email,
      username,
      role,
    });
    const refreshToken = await this.jwtService.signAsync(
      {
        sub: userId,
        email,
        username,
        role,
      },
      {
        secret: this.configService.getOrThrow<string>('JWT_REFRESH_SECRET'),
        expiresIn: (this.configService.get<string>('JWT_REFRESH_EXPIRES_IN') ??
          '30d') as StringValue,
      },
    );

    const refreshTokenHash = await bcrypt.hash(refreshToken, 10);
    await this.usersService.updateRefreshTokenHash(userId, refreshTokenHash);

    const full = await this.usersService.findById(userId);
    const membershipStatus = full
      ? this.usersService.effectiveMembership(full)
      : TeamMembershipStatus.ACTIVE;

    return {
      accessToken,
      refreshToken,
      user: {
        id: userId,
        email,
        username,
        role,
        membershipStatus,
      },
    };
  }
}
