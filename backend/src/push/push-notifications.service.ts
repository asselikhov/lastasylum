import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import * as admin from 'firebase-admin';
import { UsersService } from '../users/users.service';

@Injectable()
export class PushNotificationsService implements OnModuleInit {
  private readonly logger = new Logger(PushNotificationsService.name);
  private ready = false;

  constructor(private readonly usersService: UsersService) {}

  onModuleInit(): void {
    const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
    if (!raw) {
      this.logger.log(
        'FIREBASE_SERVICE_ACCOUNT_JSON not set; push delivery disabled',
      );
      return;
    }
    try {
      if (!admin.apps.length) {
        const cred = JSON.parse(raw) as admin.ServiceAccount;
        admin.initializeApp({
          credential: admin.credential.cert(cred),
        });
      }
      this.ready = true;
      this.logger.log('Firebase Admin initialized for FCM');
    } catch (e) {
      this.logger.warn(`Firebase Admin init failed: ${(e as Error).message}`);
    }
  }

  async notifyAllianceChatMessage(input: {
    allianceId: string;
    excludeUserId: string;
    title: string;
    body: string;
    data: Record<string, string>;
  }): Promise<void> {
    if (!this.ready) return;
    const tokens = await this.usersService.collectPushTokensForAlliance(
      input.allianceId,
      input.excludeUserId,
    );
    if (tokens.length === 0) return;
    const unique = [...new Set(tokens)].slice(0, 500);
    try {
      const res = await admin.messaging().sendEachForMulticast({
        tokens: unique,
        notification: { title: input.title, body: input.body },
        data: input.data,
        android: { priority: 'high' },
      });
      if (res.failureCount > 0) {
        this.logger.debug(
          `FCM partial failure: ${res.failureCount}/${unique.length}`,
        );
      }
    } catch (e) {
      this.logger.warn(`FCM send failed: ${(e as Error).message}`);
    }
  }
}
