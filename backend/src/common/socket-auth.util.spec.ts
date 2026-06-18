import { ConfigService } from '@nestjs/config';
import { JwtService } from '@nestjs/jwt';
import { WsException } from '@nestjs/websockets';
import {
  authenticateSocketConnection,
  authenticateSocketConnectionOrDisconnect,
  extractSocketBearerToken,
  verifySocketToken,
} from './socket-auth.util';

function mockSocket(token?: string) {
  const disconnect = jest.fn().mockResolvedValue(undefined);
  return {
    id: 'sock-1',
    handshake: {
      auth: token != null ? { token } : {},
      headers: {},
    },
    data: {},
    disconnect,
  } as unknown as Parameters<typeof authenticateSocketConnection>[0] & {
    disconnect: jest.Mock;
  };
}

describe('socket-auth.util', () => {
  const jwtService = {
    verify: jest.fn(),
  } as unknown as JwtService;
  const configService = {
    getOrThrow: jest.fn().mockReturnValue('secret'),
  } as unknown as ConfigService;

  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('extractSocketBearerToken prefers auth.token', () => {
    const client = mockSocket('from-auth');
    expect(extractSocketBearerToken(client)).toBe('from-auth');
  });

  it('verifySocketToken throws WsException on invalid jwt', () => {
    (jwtService.verify as jest.Mock).mockImplementation(() => {
      throw new Error('jwt expired');
    });
    expect(() =>
      verifySocketToken(jwtService, configService, 'bad-token'),
    ).toThrow(WsException);
  });

  it('authenticateSocketConnectionOrDisconnect disconnects instead of throwing', () => {
    (jwtService.verify as jest.Mock).mockImplementation(() => {
      throw new Error('jwt expired');
    });
    const client = mockSocket('expired');
    const user = authenticateSocketConnectionOrDisconnect(
      client,
      jwtService,
      configService,
    );
    expect(user).toBeNull();
    expect(client.disconnect).toHaveBeenCalledWith(true);
    expect(() =>
      authenticateSocketConnection(client, jwtService, configService),
    ).toThrow(WsException);
  });

  it('authenticateSocketConnectionOrDisconnect returns user on success', () => {
    (jwtService.verify as jest.Mock).mockReturnValue({
      sub: 'user-1',
      username: 'alice',
      role: 'R1',
    });
    const client = mockSocket('valid');
    const user = authenticateSocketConnectionOrDisconnect(
      client,
      jwtService,
      configService,
    );
    expect(user).toEqual({
      userId: 'user-1',
      username: 'alice',
      role: 'MEMBER',
    });
    expect(client.disconnect).not.toHaveBeenCalled();
  });
});
