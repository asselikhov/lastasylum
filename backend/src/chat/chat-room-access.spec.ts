import { Types } from 'mongoose';
import { GLOBAL_CHAT_ALLIANCE_ID } from '../common/constants/global-chat-alliance-id';
import { playerTeamChatAllianceId } from './chat-alliance-scope';
import { resolveTeamChatScope, userMayAccessChatRoom } from './chat-room-access';

describe('chat-room-access', () => {
  const teamId = new Types.ObjectId();

  it('global «Мир» is visible without a player team', () => {
    expect(
      userMayAccessChatRoom(
        { allianceName: 'SquadRelay', playerTeamId: null },
        { allianceId: GLOBAL_CHAT_ALLIANCE_ID, archivedAt: null },
      ),
    ).toBe(true);
  });

  it('team hub/raid require playerTeamId', () => {
    const scope = playerTeamChatAllianceId(teamId.toString());
    const userWithoutTeam = { allianceName: 'SquadRelay', playerTeamId: null };
    expect(
      userMayAccessChatRoom(userWithoutTeam, {
        allianceId: scope,
        archivedAt: null,
      }),
    ).toBe(false);
    expect(
      userMayAccessChatRoom(
        { allianceName: 'SquadRelay', playerTeamId: teamId },
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
