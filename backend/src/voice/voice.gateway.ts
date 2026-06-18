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
import { Namespace, Socket } from 'socket.io';
import { authenticateSocketConnectionOrDisconnect } from '../common/socket-auth.util';
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
  VOICE_CODEC_OPUS_CONFIG,
  VOICE_CODEC_PCM,
} from './voice-wire.util';

/** Android overlay joins with this sentinel; server maps to the player team's hub room. */
export const TEAM_VOICE_ROOM_AUTO = 'team';

const FRAME_WINDOW_MS = 1_000;
const MAX_FRAMES_PER_WINDOW = 55;
/** Align with overlay presence heartbeat (~60s) and Android stale window (90s). */
const OVERLAY_INGAME_CACHE_MS = 30_000;
/** Do not cache «not ingame» long — voice may start right after a fresh client ping. */
const OVERLAY_INGAME_NEGATIVE_CACHE_MS = 2_000;

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
  private readonly logger = new Logger(VoiceGateway.name);

  @WebSocketServer()
  server: Namespace;

  private readonly frameTimestamps = new Map<string, number[]>();
  private readonly overlayIngameCache = new Map<
    string,
    { until: number; value: boolean }
  >();

  constructor(
    private readonly chatService: ChatService,
    private readonly chatRoomsService: ChatRoomsService,
    private readonly usersService: UsersService,
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
    const roomId = await this.resolveVoiceRoomId(user.userId, payload?.roomId);
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
    await client.join(this.voiceRoomKey(roomId));
    await this.usersService.updatePresence(user.userId, 'ingame');
    this.setOverlayIngameCached(user.userId, true);

    const peers = await this.collectPeers(roomId, client.id);
    client.to(this.voiceRoomKey(roomId)).emit('voice:peer-joined', {
      roomId,
      peer: this.peerStateFromClient(client),
    });

    const joinedPayload = { roomId, peers };
    (client as Socket).emit('voice:joined', joinedPayload);
    return {
      event: 'voice:joined',
      data: joinedPayload,
    };
  }

  @SubscribeMessage('voice:leave')
  async leaveVoice(@ConnectedSocket() client: AuthSocket) {
    const user = this.requireUser(client);
    const roomId = client.data.voiceRoomId;
    if (!roomId) return { event: 'voice:left', data: {} };
    void client.leave(this.voiceRoomKey(roomId));
    client.data.voiceRoomId = undefined;
    client.data.micOn = false;
    client.data.soundOn = false;
    this.frameTimestamps.delete(client.id);
    this.setOverlayIngameCached(user.userId, false);
    client.to(this.voiceRoomKey(roomId)).emit('voice:peer-left', {
      roomId,
      userId: user.userId,
    });
    await this.usersService.updatePresence(user.userId, 'away');
    return { event: 'voice:left', data: { roomId } };
  }

  @SubscribeMessage('voice:state')
  async updateState(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody()
    body: { roomId?: string; micOn?: boolean; soundOn?: boolean },
  ) {
    const user = this.requireUser(client);
    const roomId = await this.resolveVoiceRoomId(user.userId, body?.roomId);
    await this.assertMayUseVoiceRoom(user.userId, roomId);
    if (client.data.voiceRoomId !== roomId) {
      throw new WsException('Join voice room before updating state');
    }

    client.data.micOn = body?.micOn === true;
    client.data.soundOn = body?.soundOn === true;

    const peer = this.peerStateFromClient(client);
    if (body?.micOn === true || body?.soundOn === true) {
      await this.usersService.updatePresence(user.userId, 'ingame');
      this.setOverlayIngameCached(user.userId, true);
    }
    this.server.to(this.voiceRoomKey(roomId)).emit('voice:peer-state', {
      roomId,
      peer,
    });
    return { event: 'voice:state-ack', data: peer };
  }

  /**
   * Relay only when sender has mic on and a fresh overlay ingame presence ping.
   * Deliver to listeners with sound on (they may be outside the game).
   */
  @SubscribeMessage('voice:frame')
  async relayFrame(
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
      parsed.codec !== VOICE_CODEC_PCM &&
      parsed.codec !== VOICE_CODEC_OPUS_CONFIG
    ) {
      throw new WsException('Unsupported codec');
    }
    if (parsed.payload.length > MAX_VOICE_FRAME_BYTES) {
      throw new WsException('Invalid frame size');
    }
    if (!(await this.overlayIngameNow(user.userId))) {
      return;
    }
    if (!this.tryRecordFrame(client.id)) {
      return;
    }

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
      const peer = this.server.sockets.get(socketId) as AuthSocket | undefined;
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
      if (body.every((x) => typeof x === 'number')) {
        return Buffer.from(body);
      }
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
    if (roomId === TEAM_VOICE_ROOM_AUTO) {
      throw new WsException('Resolve team voice room before join');
    }
    return roomId;
  }

  private async resolveVoiceRoomId(
    userId: string,
    requested?: string,
  ): Promise<string> {
    const trimmed = typeof requested === 'string' ? requested.trim() : '';
    if (!trimmed || trimmed === TEAM_VOICE_ROOM_AUTO) {
      const user = await this.usersService.findById(userId);
      if (!user) {
        throw new WsException('User not found');
      }
      try {
        return await this.chatRoomsService.findTeamVoiceRoomIdForUser(user);
      } catch {
        throw new WsException(
          'Voice is only available for members of a player team',
        );
      }
    }
    return this.parseRoomId(trimmed);
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

  private setOverlayIngameCached(userId: string, value: boolean): void {
    const cacheMs = value
      ? OVERLAY_INGAME_CACHE_MS
      : OVERLAY_INGAME_NEGATIVE_CACHE_MS;
    this.overlayIngameCache.set(userId, {
      until: Date.now() + cacheMs,
      value,
    });
  }

  private async overlayIngameNow(
    userId: string,
    bypassCache = false,
  ): Promise<boolean> {
    const now = Date.now();
    if (!bypassCache) {
      const cached = this.overlayIngameCache.get(userId);
      if (cached && cached.until > now) {
        return cached.value;
      }
    }
    const value = await this.usersService.isOverlayIngameNow(userId);
    const cacheMs = value
      ? OVERLAY_INGAME_CACHE_MS
      : OVERLAY_INGAME_NEGATIVE_CACHE_MS;
    this.overlayIngameCache.set(userId, {
      until: now + cacheMs,
      value,
    });
    return value;
  }

  private async collectPeers(
    roomId: string,
    exceptSocketId: string,
  ): Promise<VoicePeerState[]> {
    const room = this.server?.adapter.rooms.get(this.voiceRoomKey(roomId));
    if (!room) return [];
    const peers: VoicePeerState[] = [];
    for (const socketId of room) {
      if (socketId === exceptSocketId) continue;
      const peerSocket = this.server.sockets.get(socketId) as
        | AuthSocket
        | undefined;
      const peerUser = peerSocket?.data?.user;
      if (!peerUser) continue;
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

  /** Returns false when the client exceeds the frame budget (frame is silently dropped). */
  private tryRecordFrame(socketId: string): boolean {
    const now = Date.now();
    const prev = this.frameTimestamps.get(socketId) ?? [];
    const recent = prev.filter((t) => now - t < FRAME_WINDOW_MS);
    if (recent.length >= MAX_FRAMES_PER_WINDOW) {
      return false;
    }
    recent.push(now);
    this.frameTimestamps.set(socketId, recent);
    return true;
  }
}
