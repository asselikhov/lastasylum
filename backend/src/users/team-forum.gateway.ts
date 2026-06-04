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
import {
  TeamForumService,
  TeamForumMessageRow,
  TeamForumTopicPinChangedPayload,
  TeamForumMessageReactionBroadcastPayload,
} from './team-forum.service';

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
  @WebSocketServer()
  server: Server;

  constructor(
    private readonly forumService: TeamForumService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  handleConnection(client: AuthSocket) {
    authenticateSocketConnection(client, this.jwtService, this.configService);
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
    await this.forumService.listTopics(teamId, client.data.user.userId);

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

    await this.forumService.listMessages(
      teamId,
      topicId,
      client.data.user.userId,
      undefined,
      1,
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
    );

    this.broadcastNewMessage(teamId, topicId, message);
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
}
