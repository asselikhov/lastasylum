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
import { Socket, Namespace } from 'socket.io';
import { authenticateSocketConnectionOrDisconnect } from '../common/socket-auth.util';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { emitForumPersonalFanoutToSockets } from '../chat/chat-realtime-broadcast.util';
import {
  TeamForumService,
  TeamForumMessageRow,
  TeamForumTopicPinChangedPayload,
  TeamForumMessageReactionBroadcastPayload,
} from './team-forum.service';
import { TeamsService } from './teams.service';

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type SocketData = {
  user?: GatewayUser;
  lastForumRoom?: string;
  lastForumTeamRoom?: string;
};

type AuthSocket = Socket<
  Record<string, never>,
  Record<string, never>,
  Record<string, never>,
  SocketData
>;

@WebSocketGateway({
  namespace: '/team-forum',
  cors: {
    origin: parseAllowedOriginsFromEnv(process.env.ALLOWED_ORIGINS) ?? true,
  },
})
export class TeamForumGateway {
  private readonly logger = new Logger(TeamForumGateway.name);

  @WebSocketServer()
  server: Namespace;

  constructor(
    private readonly forumService: TeamForumService,
    private readonly teams: TeamsService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  handleConnection(client: AuthSocket) {
    const user = authenticateSocketConnectionOrDisconnect(
      client,
      this.jwtService,
      this.configService,
      this.logger,
    );
    if (!user) return;
    void client.join(`user:${user.userId}`);
  }

  private roomKey(teamId: string, topicId: string): string {
    return `forum:${teamId}:${topicId}`;
  }

  private teamInboxRoomKey(teamId: string): string {
    return `forum-team:${teamId}`;
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
    await this.teams.getTeamIfMemberOrThrow(teamId, client.data.user.userId);

    const key = this.teamInboxRoomKey(teamId);
    if (
      client.data.lastForumTeamRoom &&
      client.data.lastForumTeamRoom !== key
    ) {
      void client.leave(client.data.lastForumTeamRoom);
    }
    void client.join(key);
    client.data.lastForumTeamRoom = key;

    return { event: 'team:joined', data: { teamId } };
  }

  @SubscribeMessage('topic:join')
  async joinTopic(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: { teamId?: string; topicId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const teamId =
      typeof payload?.teamId === 'string' ? payload.teamId.trim() : '';
    const topicId =
      typeof payload?.topicId === 'string' ? payload.topicId.trim() : '';
    if (!teamId || !topicId) {
      throw new WsException('teamId and topicId are required');
    }

    await this.forumService.assertTopicMemberAccess(
      teamId,
      topicId,
      client.data.user.userId,
    );

    const key = this.roomKey(teamId, topicId);
    if (client.data.lastForumRoom && client.data.lastForumRoom !== key) {
      void client.leave(client.data.lastForumRoom);
    }
    void client.join(key);
    client.data.lastForumRoom = key;

    return { event: 'topic:joined', data: { teamId, topicId } };
  }

  @SubscribeMessage('message:send')
  async handleMessage(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    payload: {
      teamId?: string;
      topicId?: string;
      text?: string;
      imageFileId?: string;
      replyToMessageId?: string;
      imageFileIds?: string[];
      clientMessageId?: string;
    },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    const teamId =
      typeof payload?.teamId === 'string' ? payload.teamId.trim() : '';
    const topicId =
      typeof payload?.topicId === 'string' ? payload.topicId.trim() : '';
    const text = typeof payload?.text === 'string' ? payload.text : '';
    const imageFileId =
      typeof payload?.imageFileId === 'string'
        ? payload.imageFileId.trim()
        : undefined;
    const imageFileIds = Array.isArray(payload?.imageFileIds)
      ? payload.imageFileIds
          .map((x) => (typeof x === 'string' ? x.trim() : ''))
          .filter(Boolean)
      : [];
    const replyToMessageId =
      typeof payload?.replyToMessageId === 'string'
        ? payload.replyToMessageId.trim()
        : undefined;
    const clientMessageId =
      typeof payload?.clientMessageId === 'string'
        ? payload.clientMessageId.trim()
        : undefined;
    if (!teamId || !topicId) {
      throw new WsException('teamId and topicId are required');
    }
    const message = await this.forumService.postMessage(
      teamId,
      topicId,
      client.data.user.userId,
      text,
      replyToMessageId || null,
      imageFileIds,
      imageFileId || null,
      null,
      clientMessageId || null,
    );

    this.broadcastNewMessageWithFanout(
      teamId,
      topicId,
      message,
      client.data.user.userId,
    );
    return { event: 'message:sent', data: message };
  }

  @SubscribeMessage('typing')
  async notifyTyping(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() body: { teamId?: string; topicId?: string },
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized websocket connection');
    }
    const teamId = typeof body?.teamId === 'string' ? body.teamId.trim() : '';
    const topicId =
      typeof body?.topicId === 'string' ? body.topicId.trim() : '';
    if (!teamId || !topicId) {
      throw new WsException('teamId and topicId are required');
    }
    this.server
      ?.to(this.roomKey(teamId, topicId))
      .except(client.id)
      .emit('user:typing', {
        teamId,
        topicId,
        userId: client.data.user.userId,
        username: client.data.user.username,
      });
  }

  broadcastNewMessageWithFanout(
    teamId: string,
    topicId: string,
    message: TeamForumMessageRow,
    senderUserId: string,
  ): void {
    this.broadcastNewMessage(teamId, topicId, message);
    void this.fanOutNewMessageToEligibleUsersNotInTopicRoom(
      teamId,
      topicId,
      message,
      senderUserId,
    );
  }

  /** Path C: personal `user:{id}` for squad members not in `forum:{teamId}:{topicId}`. */
  private async fanOutNewMessageToEligibleUsersNotInTopicRoom(
    teamId: string,
    topicId: string,
    message: TeamForumMessageRow,
    senderUserId: string,
  ): Promise<void> {
    const tid = teamId.trim();
    const topId = topicId.trim();
    const uid = senderUserId.trim();
    if (!tid || !topId) return;
    const eligible = await this.teams.listSquadMemberUserIds(tid);
    const payload = { ...message, teamId: tid, topicId: topId };
    emitForumPersonalFanoutToSockets(
      this.server,
      this.server?.adapter.rooms,
      eligible,
      tid,
      topId,
      'message:new',
      payload,
      uid,
    );
  }

  private userIdsInForumTopicRoom(teamId: string, topicId: string): Set<string> {
    const out = new Set<string>();
    const adapterRoom = this.server?.adapter.rooms.get(
      this.roomKey(teamId, topicId),
    );
    if (!adapterRoom) return out;
    for (const socketId of adapterRoom) {
      const client = this.server.sockets.get(socketId) as
        | AuthSocket
        | undefined;
      const userId = client?.data?.user?.userId?.trim();
      if (userId) out.add(userId);
    }
    return out;
  }

  broadcastNewMessage(
    teamId: string,
    topicId: string,
    message: TeamForumMessageRow,
  ) {
    const payload = { ...message, teamId, topicId };
    this.server?.to(this.roomKey(teamId, topicId)).emit('message:new', payload);
    this.server?.to(this.teamInboxRoomKey(teamId)).emit('topic:activity', {
      teamId,
      topicId,
      messageId: message.id,
      senderUserId: message.senderUserId,
      createdAt: message.createdAt,
    });
  }

  broadcastMessageDeleted(
    teamId: string,
    topicId: string,
    messageId: string,
    deletedAt: string,
    deletedByUserId: string,
  ) {
    this.server?.to(this.roomKey(teamId, topicId)).emit('message:deleted', {
      teamId,
      topicId,
      messageId,
      deletedAt,
      deletedByUserId,
    });
  }

  broadcastMessageEdited(
    teamId: string,
    topicId: string,
    message: TeamForumMessageRow,
  ) {
    this.server
      ?.to(this.roomKey(teamId, topicId))
      .emit('message:edited', { ...message, teamId, topicId });
  }

  broadcastMessageReaction(payload: TeamForumMessageReactionBroadcastPayload) {
    const { teamId, topicId } = payload;
    this.server
      ?.to(this.roomKey(teamId, topicId))
      .emit('message:reaction', payload);
  }

  broadcastTopicPinChanged(payload: TeamForumTopicPinChangedPayload): void {
    const { teamId, topicId } = payload;
    this.server
      ?.to(this.roomKey(teamId, topicId))
      .emit('topic:pin-changed', payload);
    this.server
      ?.to(this.teamInboxRoomKey(teamId))
      .emit('topic:pin-changed', payload);
  }

  broadcastTopicRead(
    teamId: string,
    topicId: string,
    userId: string,
    messageId: string,
  ): void {
    this.server?.to(this.roomKey(teamId, topicId)).emit('topic:read', {
      teamId,
      topicId,
      userId,
      messageId,
    });
  }
}
