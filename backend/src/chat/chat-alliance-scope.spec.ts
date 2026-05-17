import { Types } from 'mongoose';
import {
  playerTeamChatAllianceId,
  resolveChatAllianceScope,
} from './chat-alliance-scope';

describe('chat-alliance-scope', () => {
  const teamId = new Types.ObjectId();

  it('uses pt:teamId scope when user is on a player team', () => {
    expect(
      resolveChatAllianceScope({
        allianceName: 'SquadRelay',
        playerTeamId: teamId,
      }),
    ).toBe(playerTeamChatAllianceId(teamId.toString()));
  });

  it('falls back to allianceName without a player team', () => {
    expect(
      resolveChatAllianceScope({
        allianceName: 'SquadRelay',
        playerTeamId: null,
      }),
    ).toBe('SquadRelay');
  });
});
