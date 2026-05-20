import {
  ConnectedSocket,
  MessageBody,
  SubscribeMessage,
  WebSocketGateway,
  WebSocketServer,
  WsException,
} from '@nestjs/websockets';
import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { Server, Socket } from 'socket.io';
import { authenticateSocketConnection } from '../common/socket-auth.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { TeamsService } from './teams.service';

const WS_JOIN_WINDOW_MS = 10_000;
const WS_JOIN_MAX = 12;

export type TeamPresenceSocketPayload = {
  userId: string;
  presenceStatus: string | null;
  lastPresenceAt: string | null;
};

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type SocketData = {
  user?: GatewayUser;
  teamRoom?: string;
};

type AuthSocket = Socket<
  Record<string, never>,
  Record<string, never>,
  Record<string, never>,
  SocketData
>;

@WebSocketGateway({
  namespace: '/team-presence',
  cors: {
    origin: parseAllowedOriginsFromEnv(process.env.ALLOWED_ORIGINS) ?? true,
  },
})
export class TeamPresenceGateway {
  @WebSocketServer()
  server: Server;

  private readonly wsJoinTimestamps = new Map<string, number[]>();

  constructor(
    private readonly teamsService: TeamsService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  handleConnection(client: AuthSocket) {
    authenticateSocketConnection(
      client,
      this.jwtService,
      this.configService,
    );
  }

  private teamRoomKey(teamId: string): string {
    return `team:${teamId}`;
  }

  private assertJoinRate(socketId: string): void {
    const now = Date.now();
    const prev = this.wsJoinTimestamps.get(socketId) ?? [];
    const recent = prev.filter((t) => now - t < WS_JOIN_WINDOW_MS);
    if (recent.length >= WS_JOIN_MAX) {
      throw new WsException('Too many team presence join requests');
    }
    recent.push(now);
    this.wsJoinTimestamps.set(socketId, recent);
  }

  @SubscribeMessage('team:join')
  async joinTeam(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: { teamId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const teamId =
      typeof payload?.teamId === 'string' ? payload.teamId.trim() : '';
    if (!teamId) {
      throw new WsException('teamId is required');
    }
    this.assertJoinRate(client.id);
    await this.teamsService.getTeamDetailForUser(teamId, client.data.user.userId);
    const key = this.teamRoomKey(teamId);
    if (client.data.teamRoom && client.data.teamRoom !== key) {
      void client.leave(client.data.teamRoom);
    }
    void client.join(key);
    client.data.teamRoom = key;
    return { event: 'team:joined', data: { teamId } };
  }

  /** Notify squad mates when overlay presence changes (ingame / away / online). */
  broadcastPresence(teamId: string, payload: TeamPresenceSocketPayload): void {
    const tid = teamId?.trim();
    if (!tid) return;
    this.server?.to(this.teamRoomKey(tid)).emit('team:presence', payload);
  }

  async broadcastUserPresence(userId: string): Promise<void> {
    const row = await this.teamsService.getUserPresenceBroadcastRow(userId);
    if (!row?.playerTeamId) return;
    this.broadcastPresence(row.playerTeamId, {
      userId: row.userId,
      presenceStatus: row.presenceStatus,
      lastPresenceAt: row.lastPresenceAt,
    });
  }
}
