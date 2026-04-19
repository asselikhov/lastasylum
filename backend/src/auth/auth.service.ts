import { MailerService } from '@nestjs-modules/mailer';
import {
  ConflictException,
  ForbiddenException,
  Injectable,
  Logger,
  UnauthorizedException,
} from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcrypt';
import {
  createHash,
  randomBytes,
  timingSafeEqual,
} from 'node:crypto';
import type { StringValue } from 'ms';
import { ForgotPasswordDto } from './dto/forgot-password.dto';
import { LoginDto } from './dto/login.dto';
import { RefreshTokenDto } from './dto/refresh-token.dto';
import { RegisterDto } from './dto/register.dto';
import { ResetPasswordDto } from './dto/reset-password.dto';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UserDocument } from '../users/schemas/user.schema';
import { UsersService } from '../users/users.service';

const RESET_TOKEN_TTL_MS = 45 * 60 * 1000;

@Injectable()
export class AuthService {
  private readonly logger = new Logger(AuthService.name);

  constructor(
    private readonly usersService: UsersService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
    private readonly mailerService: MailerService,
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
        user: await this.usersService.toSafeUser(user),
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

  /**
   * Always returns the same shape so callers cannot infer whether the email exists.
   */
  async forgotPassword(dto: ForgotPasswordDto): Promise<{ ok: true }> {
    const email = dto.email.toLowerCase().trim();
    const user = await this.usersService.findByEmail(email);
    if (
      user &&
      this.usersService.effectiveMembership(user) ===
        TeamMembershipStatus.ACTIVE
    ) {
      const token = randomBytes(32).toString('hex');
      const tokenHash = createHash('sha256').update(token, 'utf8').digest('hex');
      const expires = new Date(Date.now() + RESET_TOKEN_TTL_MS);
      await this.usersService.setPasswordResetToken(email, tokenHash, expires);
      const appName =
        this.configService.get<string>('APP_PUBLIC_NAME')?.trim() ||
        'SquadRelay';
      try {
        await this.mailerService.sendMail({
          to: email,
          subject: `${appName}: сброс пароля`,
          text: `Скопируйте токен в приложение (действителен 45 минут):\n\n${token}\n`,
          html: `<p>Скопируйте токен в приложение (действителен 45 минут):</p><pre style="word-break:break-all">${token}</pre>`,
        });
      } catch (err) {
        this.logger.error(
          `Failed to send password reset email to ${email}`,
          err instanceof Error ? err.stack : String(err),
        );
      }
    }
    return { ok: true };
  }

  async resetPassword(dto: ResetPasswordDto): Promise<{ ok: true }> {
    const email = dto.email.toLowerCase().trim();
    const user = await this.usersService.findByEmail(email);
    if (
      !user?.passwordResetTokenHash ||
      !user.passwordResetExpires ||
      user.passwordResetExpires.getTime() < Date.now()
    ) {
      throw new UnauthorizedException('Invalid or expired reset token');
    }
    if (
      this.usersService.effectiveMembership(user) !==
      TeamMembershipStatus.ACTIVE
    ) {
      throw new UnauthorizedException('Invalid or expired reset token');
    }
    const digest = createHash('sha256')
      .update(dto.token.trim(), 'utf8')
      .digest('hex');
    const stored = user.passwordResetTokenHash;
    let valid = false;
    try {
      const a = Buffer.from(digest, 'hex');
      const b = Buffer.from(stored, 'hex');
      valid = a.length === b.length && timingSafeEqual(a, b);
    } catch {
      valid = false;
    }
    if (!valid) {
      throw new UnauthorizedException('Invalid or expired reset token');
    }
    const newHash = await bcrypt.hash(dto.newPassword, 10);
    const updated = await this.usersService.applyPasswordReset(email, newHash);
    if (!updated) {
      throw new UnauthorizedException('Invalid or expired reset token');
    }
    return { ok: true };
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
    if (!full) {
      throw new UnauthorizedException('User not found');
    }

    const user = await this.usersService.toSafeUser(full);

    return {
      accessToken,
      refreshToken,
      user,
    };
  }
}
