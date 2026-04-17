import { MailerService } from '@nestjs-modules/mailer';
import { JwtService } from '@nestjs/jwt';
import { Test } from '@nestjs/testing';
import { createHash } from 'node:crypto';
import { ConfigService } from '@nestjs/config';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { UsersService } from '../users/users.service';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  const sendMail = jest.fn().mockResolvedValue(undefined);
  const findByEmail = jest.fn();
  const setPasswordResetToken = jest.fn().mockResolvedValue(undefined);
  const applyPasswordReset = jest.fn().mockResolvedValue({ _id: 'u1' });
  const effectiveMembership = jest.fn();

  let authService: AuthService;

  beforeEach(async () => {
    jest.clearAllMocks();
    const moduleRef = await Test.createTestingModule({
      providers: [
        AuthService,
        {
          provide: UsersService,
          useValue: {
            findByEmail,
            effectiveMembership,
            setPasswordResetToken,
            applyPasswordReset,
          },
        },
        { provide: MailerService, useValue: { sendMail } },
        {
          provide: JwtService,
          useValue: {
            signAsync: jest.fn().mockResolvedValue('jwt'),
          },
        },
        {
          provide: ConfigService,
          useValue: {
            get: jest.fn((key: string) => {
              if (key === 'JWT_REFRESH_EXPIRES_IN') return '30d';
              if (key === 'JWT_EXPIRES_IN') return '7d';
              if (key === 'APP_PUBLIC_NAME') return 'TestApp';
              return undefined;
            }),
            getOrThrow: jest.fn((key: string) => {
              if (key === 'JWT_SECRET') return 's'.repeat(32);
              if (key === 'JWT_REFRESH_SECRET') return 'r'.repeat(32);
              throw new Error(`missing ${key}`);
            }),
          },
        },
      ],
    }).compile();

    authService = moduleRef.get(AuthService);
  });

  describe('forgotPassword', () => {
    it('returns ok without sending when user not found', async () => {
      findByEmail.mockResolvedValue(null);
      const out = await authService.forgotPassword({ email: 'nope@example.com' });
      expect(out).toEqual({ ok: true });
      expect(sendMail).not.toHaveBeenCalled();
      expect(setPasswordResetToken).not.toHaveBeenCalled();
    });

    it('stores token hash and sends mail for active user', async () => {
      findByEmail.mockResolvedValue({
        email: 'a@example.com',
        membershipStatus: TeamMembershipStatus.ACTIVE,
      });
      effectiveMembership.mockReturnValue(TeamMembershipStatus.ACTIVE);

      const out = await authService.forgotPassword({ email: 'a@example.com' });
      expect(out).toEqual({ ok: true });
      expect(setPasswordResetToken).toHaveBeenCalledTimes(1);
      const [email, hash] = setPasswordResetToken.mock.calls[0] as [
        string,
        string,
        Date,
      ];
      expect(email).toBe('a@example.com');
      expect(hash).toMatch(/^[a-f0-9]{64}$/);
      expect(sendMail).toHaveBeenCalled();
    });
  });

  describe('resetPassword', () => {
    it('rejects invalid token', async () => {
      const token = 'b'.repeat(64);
      const digest = createHash('sha256').update(token, 'utf8').digest('hex');
      findByEmail.mockResolvedValue({
        email: 'a@example.com',
        passwordResetTokenHash: digest,
        passwordResetExpires: new Date(Date.now() + 60_000),
      });
      effectiveMembership.mockReturnValue(TeamMembershipStatus.ACTIVE);

      await expect(
        authService.resetPassword({
          email: 'a@example.com',
          token: 'c'.repeat(64),
          newPassword: 'newpass123',
        }),
      ).rejects.toBeDefined();
      expect(applyPasswordReset).not.toHaveBeenCalled();
    });

    it('applies new password when token matches', async () => {
      const token = 'd'.repeat(64);
      const digest = createHash('sha256').update(token, 'utf8').digest('hex');
      findByEmail.mockResolvedValue({
        email: 'a@example.com',
        passwordResetTokenHash: digest,
        passwordResetExpires: new Date(Date.now() + 60_000),
      });
      effectiveMembership.mockReturnValue(TeamMembershipStatus.ACTIVE);

      const out = await authService.resetPassword({
        email: 'a@example.com',
        token,
        newPassword: 'newpass1234',
      });
      expect(out).toEqual({ ok: true });
      expect(applyPasswordReset).toHaveBeenCalled();
      const [, hash] = applyPasswordReset.mock.calls[0];
      expect(typeof hash).toBe('string');
      expect((hash as string).length).toBeGreaterThan(10);
    });
  });
});
