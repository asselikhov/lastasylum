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

  async notifyExcavationAlert(input: {
    allianceId: string;
    excludeUserId: string;
    senderName: string;
    body: string;
    data: Record<string, string>;
  }): Promise<void> {
    if (!this.ready) return;
    const tokens = await this.usersService.collectPushTokensForExcavationAlert(
      input.allianceId,
      input.excludeUserId,
    );
    if (tokens.length === 0) {
      this.logger.warn(
        `FCM excavation: no device tokens for allianceId=${input.allianceId} ` +
          `(excludeUserId=${input.excludeUserId}). Recipients need pushFcmTokens and must not be fresh ingame.`,
      );
      return;
    }
    const unique = [...new Set(tokens)].slice(0, 500);
    const title = '⛏ Раскопки';
    const body =
      input.body.trim().length > 0
        ? input.body.trim()
        : 'Союзники отметили координаты раскопок';
    try {
      const res = await admin.messaging().sendEachForMulticast({
        tokens: unique,
        notification: { title, body },
        data: {
          ...input.data,
          type: 'excavation_alert',
          senderName: input.senderName,
        },
        android: {
          priority: 'high',
          notification: {
            channelId: 'excavation_alerts',
            priority: 'max',
            defaultSound: true,
            defaultVibrateTimings: true,
            visibility: 'public',
          },
        },
        apns: {
          headers: { 'apns-priority': '10' },
          payload: {
            aps: {
              alert: { title, body },
              sound: 'default',
            },
          },
        },
      });
      if (res.failureCount > 0) {
        this.logger.warn(
          `FCM excavation partial failure: ${res.failureCount}/${unique.length}`,
        );
      } else {
        this.logger.log(
          `FCM excavation sent to ${unique.length} device token(s) (allianceId=${input.allianceId})`,
        );
      }
    } catch (e) {
      this.logger.warn(`FCM excavation send failed: ${(e as Error).message}`);
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
