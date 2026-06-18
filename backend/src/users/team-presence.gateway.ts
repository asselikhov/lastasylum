import { Logger } from '@nestjs/common';
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
import { authenticateSocketConnectionOrDisconnect } from '../common/socket-auth.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { TeamsService } from './teams.service';

export type TeamPresenceSocketPayload = {
  userId: string;
  presenceStatus: string | null;
  lastPresenceAt: string | null;
  username?: string | null;
  teamRole?: string | null;
  isLeader?: boolean;
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
  private readonly logger = new Logger(TeamPresenceGateway.name);

  @WebSocketServer()
  server: Server;

  constructor(
    private readonly teamsService: TeamsService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  handleConnection(client: AuthSocket) {
    authenticateSocketConnectionOrDisconnect(
      client,
      this.jwtService,
      this.configService,
      this.logger,
    );
  }

  private teamRoomKey(teamId: string): string {
    return `team:${teamId}`;
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
    await this.teamsService.getTeamDetailForUser(
      teamId,
      client.data.user.userId,
    );
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
      username: row.username,
      teamRole: row.teamRole,
      isLeader: row.isLeader,
    });
  }
}
