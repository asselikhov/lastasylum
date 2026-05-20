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
import { Namespace, Socket } from 'socket.io';
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { TeamMembershipStatus } from '../common/enums/team-membership-status.enum';
import { userMayAccessChatRoom } from '../chat/chat-room-access';
import { UsersService } from '../users/users.service';
import { ChatRoomsService } from '../chat/chat-rooms.service';
import { parseAllowedOriginsFromEnv } from '../common/config/allowed-origins';
import { ChatService } from '../chat/chat.service';
import {
  MAX_VOICE_FRAME_BYTES,
  packDownstreamFrame,
  parseUpstreamFrame,
  VOICE_CODEC_OPUS,
  VOICE_CODEC_PCM,
} from './voice-wire.util';

const FRAME_WINDOW_MS = 1_000;
const MAX_FRAMES_PER_WINDOW = 55;

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type SocketData = {
  user?: GatewayUser;
  voiceRoomId?: string;
  micOn?: boolean;
  soundOn?: boolean;
};

type AuthSocket = Socket<
  Record<string, never>,
  Record<string, never>,
  Record<string, never>,
  SocketData
>;

type VoicePeerState = {
  userId: string;
  username: string;
  micOn: boolean;
  soundOn: boolean;
};

@WebSocketGateway({
  namespace: '/voice',
  cors: {
    origin: parseAllowedOriginsFromEnv(process.env.ALLOWED_ORIGINS) ?? true,
  },
  maxHttpBufferSize: 1e6,
})
export class VoiceGateway {
  @WebSocketServer()
  server: Namespace;

  private readonly frameTimestamps = new Map<string, number[]>();

  constructor(
    private readonly chatService: ChatService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly usersService: UsersService,
    private readonly jwtService: JwtService,
    private readonly configService: ConfigService,
  ) {}

  handleConnection(client: AuthSocket) {
    const authPayload = client.handshake.auth as { token?: unknown };
    const authTokenValue = authPayload.token;
    const headerValue = client.handshake.headers.authorization;
    const headerToken =
      typeof headerValue === 'string'
        ? headerValue.replace(/^Bearer\s+/i, '')
        : undefined;
    const rawToken =
      typeof authTokenValue === 'string' ? authTokenValue : headerToken;

    if (!rawToken) {
      throw new WsException('Missing websocket token');
    }

    let payload: GatewayUser & { sub: string };
    try {
      payload = this.jwtService.verify<GatewayUser & { sub: string }>(
        rawToken,
        {
          secret: this.configService.getOrThrow<string>('JWT_SECRET'),
        },
      );
    } catch {
      throw new WsException('Invalid websocket token');
    }
    client.data.user = {
      userId: payload.sub,
      username: payload.username,
      role: payload.role,
    } satisfies GatewayUser;
    client.data.micOn = false;
    client.data.soundOn = false;
  }

  handleDisconnect(client: AuthSocket) {
    const roomId = client.data.voiceRoomId;
    const user = client.data.user;
    if (!roomId || !user) return;
    this.frameTimestamps.delete(client.id);
    client.to(this.voiceRoomKey(roomId)).emit('voice:peer-left', {
      roomId,
      userId: user.userId,
    });
  }

  @SubscribeMessage('voice:join')
  async joinVoice(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    payload: { roomId?: string; micOn?: boolean; soundOn?: boolean },
  ) {
    const user = this.requireUser(client);
    const roomId = this.parseRoomId(payload?.roomId);
    await this.assertMayUseVoiceRoom(user.userId, roomId);

    const prevRoom = client.data.voiceRoomId;
    if (prevRoom && prevRoom !== roomId) {
      void client.leave(this.voiceRoomKey(prevRoom));
      client.to(this.voiceRoomKey(prevRoom)).emit('voice:peer-left', {
        roomId: prevRoom,
        userId: user.userId,
      });
    }

    client.data.voiceRoomId = roomId;
    /** Apply with join so state is valid before any relay / peer snapshots (avoids race with voice:state). */
    client.data.micOn = payload?.micOn === true;
    client.data.soundOn = payload?.soundOn === true;
    void client.join(this.voiceRoomKey(roomId));

    const peers = this.collectPeers(roomId, client.id);
    client.to(this.voiceRoomKey(roomId)).emit('voice:peer-joined', {
      roomId,
      peer: this.peerStateFromClient(client),
    });

    return {
      event: 'voice:joined',
      data: { roomId, peers },
    };
  }

  @SubscribeMessage('voice:leave')
  leaveVoice(@ConnectedSocket() client: AuthSocket) {
    const user = this.requireUser(client);
    const roomId = client.data.voiceRoomId;
    if (!roomId) return { event: 'voice:left', data: {} };
    void client.leave(this.voiceRoomKey(roomId));
    client.data.voiceRoomId = undefined;
    client.data.micOn = false;
    client.data.soundOn = false;
    this.frameTimestamps.delete(client.id);
    client.to(this.voiceRoomKey(roomId)).emit('voice:peer-left', {
      roomId,
      userId: user.userId,
    });
    return { event: 'voice:left', data: { roomId } };
  }

  @SubscribeMessage('voice:state')
  async updateState(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    body: { roomId?: string; micOn?: boolean; soundOn?: boolean },
  ) {
    const user = this.requireUser(client);
    const roomId = this.parseRoomId(body?.roomId);
    await this.assertMayUseVoiceRoom(user.userId, roomId);
    if (client.data.voiceRoomId !== roomId) {
      throw new WsException('Join voice room before updating state');
    }

    client.data.micOn = body?.micOn === true;
    client.data.soundOn = body?.soundOn === true;

    const peer = this.peerStateFromClient(client);
    client.to(this.voiceRoomKey(roomId)).emit('voice:peer-state', {
      roomId,
      peer,
    });
    return { event: 'voice:state-ack', data: peer };
  }

  /** Binary upstream frame; relay only to listeners with soundOn. */
  @SubscribeMessage('voice:frame')
  relayFrame(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() body: Buffer,
  ) {
    const user = this.requireUser(client);
    if (!client.data.micOn) {
      return;
    }
    const roomId = client.data.voiceRoomId;
    if (!roomId) {
      throw new WsException('Not in a voice room');
    }

    const raw = this.coerceBuffer(body);
    const parsed = parseUpstreamFrame(raw);
    if (!parsed) {
      throw new WsException('Invalid voice frame');
    }
    if (
      parsed.codec !== VOICE_CODEC_OPUS &&
      parsed.codec !== VOICE_CODEC_PCM
    ) {
      throw new WsException('Unsupported codec');
    }
    if (parsed.payload.length > MAX_VOICE_FRAME_BYTES) {
      throw new WsException('Invalid frame size');
    }
    this.assertFrameRate(client.id);

    const downstream = packDownstreamFrame(
      user.userId,
      parsed.codec,
      parsed.seq,
      parsed.payload,
    );
    this.emitFrameToSoundOnListeners(roomId, client.id, downstream);
  }

  private emitFrameToSoundOnListeners(
    roomId: string,
    exceptSocketId: string,
    packet: Buffer,
  ): void {
    const room = this.server?.adapter.rooms.get(this.voiceRoomKey(roomId));
    if (!room) return;
    for (const socketId of room) {
      if (socketId === exceptSocketId) continue;
      const peer = this.server.sockets.get(socketId) as
        | AuthSocket
        | undefined;
      if (!peer?.data?.soundOn) continue;
      (peer as Socket).emit('voice:frame', packet);
    }
  }

  private coerceBuffer(body: unknown): Buffer {
    if (Buffer.isBuffer(body)) return body;
    if (body instanceof ArrayBuffer) return Buffer.from(body);
    if (ArrayBuffer.isView(body)) {
      return Buffer.from(body.buffer, body.byteOffset, body.byteLength);
    }
    if (Array.isArray(body) && body.length > 0) {
      return this.coerceBuffer(body[0]);
    }
    throw new WsException('Expected binary voice frame');
  }

  private voiceRoomKey(roomId: string): string {
    return `voice:${roomId}`;
  }

  private requireUser(client: AuthSocket): GatewayUser {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }
    return client.data.user;
  }

  private parseRoomId(value: unknown): string {
    const roomId = typeof value === 'string' ? value.trim() : '';
    if (!roomId) {
      throw new WsException('roomId is required');
    }
    return roomId;
  }

  private async assertMayUseVoiceRoom(
    userId: string,
    roomId: string,
  ): Promise<void> {
    await this.chatService.assertUserMayUseChat(userId);
    const user = await this.usersService.findById(userId);
    const room = await this.chatRoomsService.findById(roomId);
    if (!user || !room || room.archivedAt) {
      throw new WsException('Room not found');
    }
    if (!userMayAccessChatRoom(user, room)) {
      throw new WsException('Room is not available for your alliance');
    }
    if (
      this.usersService.effectiveMembership(user) !==
      TeamMembershipStatus.ACTIVE
    ) {
      throw new WsException('Voice is not available for this account');
    }
  }

  private collectPeers(
    roomId: string,
    exceptSocketId: string,
  ): VoicePeerState[] {
    const room = this.server?.adapter.rooms.get(this.voiceRoomKey(roomId));
    if (!room) return [];
    const peers: VoicePeerState[] = [];
    for (const socketId of room) {
      if (socketId === exceptSocketId) continue;
      const peerSocket = this.server.sockets.get(socketId) as
        | AuthSocket
        | undefined;
      if (!peerSocket?.data?.user) continue;
      peers.push(this.peerStateFromClient(peerSocket));
    }
    return peers;
  }

  private peerStateFromClient(client: AuthSocket): VoicePeerState {
    const user = client.data.user!;
    return {
      userId: user.userId,
      username: user.username,
      micOn: client.data.micOn === true,
      soundOn: client.data.soundOn === true,
    };
  }

  private assertFrameRate(socketId: string): void {
    const now = Date.now();
    const prev = this.frameTimestamps.get(socketId) ?? [];
    const recent = prev.filter((t) => now - t < FRAME_WINDOW_MS);
    if (recent.length >= MAX_FRAMES_PER_WINDOW) {
      throw new WsException('Voice frame rate exceeded');
    }
    recent.push(now);
    this.frameTimestamps.set(socketId, recent);
  }
}
