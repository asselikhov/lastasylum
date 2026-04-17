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
import { AllianceRole } from '../common/enums/alliance-role.enum';
import { CreateMessageDto } from './dto/create-message.dto';
import { ChatService } from './chat.service';

type GatewayUser = {
  userId: string;
  username: string;
  role: AllianceRole;
};

type AuthSocket = Socket<
  Record<string, never>,
  Record<string, never>,
  Record<string, never>,
  { user?: GatewayUser }
>;

@WebSocketGateway({
  namespace: '/chat',
  cors: {
    origin: '*',
  },
})
export class ChatGateway {
  @WebSocketServer()
  server: Server;

  constructor(
    private readonly chatService: ChatService,
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
  }

  @SubscribeMessage('room:join')
  joinRoom(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: { allianceId: string },
  ) {
    const allianceId = payload.allianceId || 'OBZHORY';
    void client.join(allianceId);
    return { event: 'room:joined', data: { allianceId } };
  }

  @SubscribeMessage('message:send')
  async handleMessage(
    @ConnectedSocket() client: AuthSocket,
    @MessageBody() payload: CreateMessageDto,
  ) {
    if (!client.data.user) {
      throw new WsException('Unauthorized socket connection');
    }

    const allianceId = payload.allianceId ?? 'OBZHORY';
    const message = await this.chatService.createMessage({
      allianceId,
      text: payload.text,
      author: client.data.user,
    });

    this.broadcastNewMessage(allianceId, message);
    return { event: 'message:sent', data: message };
  }

  broadcastNewMessage(allianceId: string, message: unknown) {
    this.server?.to(allianceId).emit('message:new', message);
  }
}
