import { Injectable, Logger, OnModuleInit } from '@nestjs/common';
import * as admin from 'firebase-admin';
import { getGameEventById } from '../game-events/game-event-catalog';
import { formatGameEventPushSenderLine } from '../game-events/game-event-push.util';
import { UsersService } from '../users/users.service';
import { parseFirebaseServiceAccountJson } from './firebase-service-account.util';

const CHAT_PUSH_DEBOUNCE_MS = 45_000;

@Injectable()
export class PushNotificationsService implements OnModuleInit {
  private readonly logger = new Logger(PushNotificationsService.name);
  private ready = false;
  /** roomId → last alliance chat push sent (debounce burst traffic). */
  private readonly chatPushDebounceByRoom = new Map<string, number>();

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
      const cred = parseFirebaseServiceAccountJson(raw);
      if (!admin.apps.length) {
        admin.initializeApp({
          credential: admin.credential.cert(cred),
        });
      }
      this.ready = true;
      const projectId =
        (cred as { project_id?: string }).project_id ?? 'unknown';
      this.logger.log(`Firebase Admin initialized for FCM (project=${projectId})`);
    } catch (e) {
      this.logger.warn(
        `Firebase Admin init failed: ${(e as Error).message}. ` +
          `Check FIREBASE_SERVICE_ACCOUNT_JSON is valid single-line JSON on Render.`,
      );
    }
  }

  /** @deprecated Use notifyGameEventAlert */
  async notifyExcavationAlert(input: {
    allianceId: string;
    excludeUserId: string;
    senderName: string;
    body: string;
    data: Record<string, string>;
  }): Promise<void> {
    return this.notifyGameEventAlert({
      allianceId: input.allianceId,
      excludeUserId: input.excludeUserId,
      eventId: 'hq_excavation',
      senderName: input.senderName,
      body: input.body,
      data: input.data,
    });
  }

  async notifyGameEventAlert(input: {
    allianceId: string;
    excludeUserId: string;
    eventId: string;
    senderName: string;
    senderLine?: string;
    senderAvatarRelativeUrl?: string;
    senderSquadRole?: string;
    senderTeamTag?: string;
    senderServerNumber?: number | null;
    senderTeamDisplayName?: string;
    body: string;
    data: Record<string, string>;
  }): Promise<void> {
    if (!this.ready) return;
    const event = getGameEventById(input.eventId);
    if (!event) {
      this.logger.warn(`FCM game event: unknown eventId=${input.eventId}`);
      return;
    }
    const tokens = await this.usersService.collectPushTokensForGameEvent(
      input.allianceId,
      input.eventId,
      input.excludeUserId,
    );
    if (tokens.length === 0) {
      this.logger.warn(
        `FCM game event: no device tokens for allianceId=${input.allianceId} ` +
          `eventId=${input.eventId} excludeUserId=${input.excludeUserId} ` +
          `(see prior FCM game event log for candidate/exclusion breakdown; ` +
          `check Mongo pushFcmTokens, gameEventPushEnabled, overlay-ingame filter, FIREBASE_SERVICE_ACCOUNT_JSON)`,
      );
      return;
    }
    const unique = [...new Set(tokens)].slice(0, 500);
    this.logger.log(
      `FCM game event dispatch: eventId=${input.eventId} allianceId=${input.allianceId} ` +
        `tokens=${unique.length} excludeUserId=${input.excludeUserId}`,
    );
    const title = event.messageText;
    const sender = input.senderName.trim();
    const senderLine =
      (input.senderLine ?? '').trim() ||
      formatGameEventPushSenderLine({
        username: sender,
        teamTag: input.senderTeamTag ?? null,
        serverNumber: input.senderServerNumber ?? null,
      });
    const notificationTitle = senderLine.length > 0 ? senderLine : title;
    const senderAvatar = (input.senderAvatarRelativeUrl ?? '').trim();
    const senderSquadRole = (input.senderSquadRole ?? '').trim().toUpperCase();
    const teamTag = (input.senderTeamTag ?? '').trim();
    const teamDisplayName = (input.senderTeamDisplayName ?? '').trim();
    const serverNum = input.senderServerNumber;
    try {
      const res = await admin.messaging().sendEachForMulticast({
        tokens: unique,
        notification: {
          title: notificationTitle,
          body: title,
        },
        data: {
          ...input.data,
          type: 'game_event_alert',
          eventId: event.id,
          category: event.category,
          channelId: event.channelId,
          senderName: sender,
          senderLine,
          senderAvatarRelativeUrl: senderAvatar,
          senderSquadRole: senderSquadRole,
          senderTeamTag: teamTag,
          teamDisplayName,
          senderServerNumber:
            typeof serverNum === 'number' && serverNum >= 1
              ? String(serverNum)
              : '',
          title,
          eventText: title,
        },
        android: {
          priority: 'high',
          notification: {
            channelId: event.channelId,
            priority: 'high',
          },
        },
        apns: {
          headers: { 'apns-priority': '10' },
          payload: {
            aps: {
              alert: {
                title: senderLine.length > 0 ? senderLine : title,
                body: title,
              },
              sound: 'default',
            },
          },
        },
      });
      await this.pruneInvalidTokens(unique, res);
      if (res.failureCount > 0) {
        const failureCodes = res.responses
          .map((r, i) =>
            r.success
              ? null
              : `${unique[i]?.slice(0, 8)}…:${r.error?.code ?? 'unknown'}`,
          )
          .filter(Boolean)
          .slice(0, 8)
          .join(', ');
        this.logger.warn(
          `FCM game event partial failure: ${res.failureCount}/${unique.length} ` +
            `eventId=${event.id} failures=[${failureCodes}]`,
        );
      } else {
        this.logger.log(
          `FCM game event sent to ${unique.length} token(s) eventId=${event.id} allianceId=${input.allianceId}`,
        );
      }
    } catch (e) {
      this.logger.warn(
        `FCM game event send failed eventId=${event.id}: ${(e as Error).message}`,
      );
    }
  }

  async notifyRaidPinAlert(input: {
    allianceId: string;
    excludeUserId: string;
    roomId: string;
    messageId: string;
    body: string;
  }): Promise<void> {
    if (!this.ready) return;
    await this.notifyAllianceChatMessage({
      allianceId: input.allianceId,
      excludeUserId: input.excludeUserId,
      title: 'Рейд: закреплено сообщение',
      body: input.body.trim().slice(0, 120) || 'Закреплено сообщение',
      data: {
        roomId: input.roomId,
        messageId: input.messageId,
        type: 'raid_pin',
      },
    });
  }

  async notifyAllianceChatMessage(input: {
    allianceId: string;
    excludeUserId: string;
    title: string;
    body: string;
    data: Record<string, string>;
  }): Promise<void> {
    if (!this.ready) return;
    const roomId = input.data.roomId?.trim() ?? '';
    if (roomId) {
      const now = Date.now();
      const last = this.chatPushDebounceByRoom.get(roomId) ?? 0;
      if (now - last < CHAT_PUSH_DEBOUNCE_MS) {
        return;
      }
      this.chatPushDebounceByRoom.set(roomId, now);
    }
    const tokens = await this.usersService.collectPushTokensForAlliance(
      input.allianceId,
      input.excludeUserId,
    );
    if (tokens.length === 0) return;
    const unique = [...new Set(tokens)].slice(0, 500);
    const title = input.title.trim();
    const body = input.body.trim();
    try {
      const res = await admin.messaging().sendEachForMulticast({
        tokens: unique,
        notification: { title, body },
        data: input.data,
        android: { priority: 'high' },
      });
      await this.pruneInvalidTokens(unique, res);
      if (res.failureCount > 0) {
        this.logger.debug(
          `FCM partial failure: ${res.failureCount}/${unique.length}`,
        );
      }
    } catch (e) {
      this.logger.warn(`FCM send failed: ${(e as Error).message}`);
    }
  }

  private async pruneInvalidTokens(
    tokens: string[],
    res: admin.messaging.BatchResponse,
  ): Promise<void> {
    const invalid: string[] = [];
    res.responses.forEach((r, i) => {
      if (r.success) return;
      const code = r.error?.code ?? '';
      if (
        code === 'messaging/invalid-registration-token' ||
        code === 'messaging/registration-token-not-registered'
      ) {
        invalid.push(tokens[i]);
      }
    });
    if (invalid.length > 0) {
      await this.usersService.removeInvalidPushTokens(invalid);
    }
  }
}
