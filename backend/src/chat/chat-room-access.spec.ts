import { Types } from 'mongoose';
import {
  GLOBAL_CHAT_ALLIANCE_ID,
  serverChatAllianceId,
} from '../common/constants/chat-room-constants';
import { playerTeamChatAllianceId } from './chat-alliance-scope';
import {
  resolveTeamChatScope,
  userMayAccessChatRoom,
} from './chat-room-access';

describe('chat-room-access', () => {
  const teamId = new Types.ObjectId();

  it('global «Межсерв» is visible without a player team', () => {
    expect(
      userMayAccessChatRoom(
        {
          allianceName: 'SquadRelay',
          playerTeamId: null,
          gameIdentities: [],
          activeGameIdentityId: null,
        },
        { allianceId: GLOBAL_CHAT_ALLIANCE_ID, archivedAt: null },
      ),
    ).toBe(true);
  });

  it('server room requires matching active server', () => {
    const serverScope = serverChatAllianceId(109);
    const identityId = new Types.ObjectId();
    const user = {
      allianceName: 'SquadRelay',
      playerTeamId: null,
      gameIdentities: [
        {
          _id: identityId,
          serverNumber: 109,
          gameNickname: 'nick',
          playerTeamId: null,
        },
      ],
      activeGameIdentityId: identityId,
    };
    expect(
      userMayAccessChatRoom(user, {
        allianceId: serverScope,
        archivedAt: null,
      }),
    ).toBe(true);
    expect(
      userMayAccessChatRoom(user, {
        allianceId: serverChatAllianceId(110),
        archivedAt: null,
      }),
    ).toBe(false);
  });

  it('team hub/raid require playerTeamId', () => {
    const scope = playerTeamChatAllianceId(teamId.toString());
    const userWithoutTeam = {
      allianceName: 'SquadRelay',
      playerTeamId: null,
      gameIdentities: [],
      activeGameIdentityId: null,
    };
    expect(
      userMayAccessChatRoom(userWithoutTeam, {
        allianceId: scope,
        archivedAt: null,
      }),
    ).toBe(false);
    expect(
      userMayAccessChatRoom(
        {
          allianceName: 'SquadRelay',
          playerTeamId: teamId,
          gameIdentities: [],
          activeGameIdentityId: null,
        },
        { allianceId: scope, archivedAt: null },
      ),
    ).toBe(true);
  });

  it('resolveTeamChatScope is null without a team', () => {
    expect(resolveTeamChatScope({ playerTeamId: null })).toBeNull();
    expect(resolveTeamChatScope({ playerTeamId: teamId })).toBe(
      playerTeamChatAllianceId(teamId.toString()),
    );
  });
});
