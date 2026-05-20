/**
 * Repair squad roster ↔ users.playerTeamId ↔ gameIdentities[].playerTeamId.
 *
 * backend/.env:
 *   MONGODB_URI=...
 *   MONGODB_DB_NAME=last_asylum
 *   SCRIPT_TEAM_TAG=OBZH          (optional, default OBZH)
 *   SCRIPT_ALLIANCE_SCOPE=OBZHORY (optional, sync allianceName on roster)
 *
 * Usage (from repo root):
 *   node backend/scripts/repair-squad-team-bindings.mjs
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
const TEAM_TAG = (env.SCRIPT_TEAM_TAG ?? 'OBZH').trim();
const ALLIANCE_SCOPE = (env.SCRIPT_ALLIANCE_SCOPE ?? 'OBZHORY').trim();

if (!uri) {
  console.error('Missing MONGODB_URI in backend/.env');
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const db = mongoose.connection.db;
const teamsCol = db.collection('playerteams');
const usersCol = db.collection('users');

const team =
  (await teamsCol.findOne({ tag: TEAM_TAG })) ||
  (await teamsCol.findOne({ displayName: new RegExp('ОБЖОР', 'i') }));

if (!team) {
  console.error(`Team not found (tag="${TEAM_TAG}")`);
  process.exit(1);
}

const teamId = team._id;
const teamTag = team.tag;
const teamDisplayName = team.displayName || TEAM_TAG;
const leaderId = team.leaderUserId;

let squadMembers = Array.isArray(team.squadMembers) ? [...team.squadMembers] : [];
if (squadMembers.length === 0 && Array.isArray(team.memberUserIds)) {
  squadMembers = team.memberUserIds.map((userId) => ({
    userId,
    role: userId.equals(leaderId) ? 'R5' : 'R1',
  }));
  await teamsCol.updateOne(
    { _id: teamId },
    { $set: { squadMembers }, $unset: { memberUserIds: '' } },
  );
}

const rosterUserIds = squadMembers.map((m) => m.userId.toString());
const roleByUserId = new Map(
  squadMembers.map((m) => [m.userId.toString(), m.role || 'R1']),
);

const report = {
  team: {
    id: teamId.toString(),
    tag: teamTag,
    displayName: teamDisplayName,
    rosterSize: rosterUserIds.length,
  },
  repaired: [],
  skipped: [],
  clearedOrphans: [],
};

for (const uid of rosterUserIds) {
  const role =
    uid === leaderId.toString() ? 'R5' : roleByUserId.get(uid) ?? 'R1';
  const user = await usersCol.findOne({ _id: new mongoose.Types.ObjectId(uid) });
  if (!user) {
    report.skipped.push({ userId: uid, reason: 'user_not_found' });
    continue;
  }

  const identities = (user.gameIdentities ?? []).map((g) => ({
    _id: g._id,
    serverNumber: g.serverNumber,
    gameNickname: g.gameNickname,
    playerTeamId: teamId,
  }));

  if (identities.length === 0) {
    const identityId = new mongoose.Types.ObjectId();
    const serverNumber = 1;
    const nick = (user.username ?? 'player').trim().slice(0, 32);
    identities.push({
      _id: identityId,
      serverNumber,
      gameNickname: nick,
      playerTeamId: teamId,
    });
    await usersCol.updateOne(
      { _id: user._id },
      {
        $set: {
          gameIdentities: identities,
          activeGameIdentityId: identityId,
        },
      },
    );
  }

  const before = {
    playerTeamId: user.playerTeamId?.toString() ?? null,
    identityTeams: (user.gameIdentities ?? []).map((g) =>
      g.playerTeamId?.toString() ?? null,
    ),
    squadRole: role,
  };

  await usersCol.updateOne(
    { _id: user._id },
    {
      $set: {
        gameIdentities: identities,
        playerTeamId: teamId,
        teamTag,
        teamDisplayName,
        allianceName: ALLIANCE_SCOPE,
        membershipStatus: user.membershipStatus ?? 'active',
      },
    },
  );

  const afterMismatch =
    before.playerTeamId !== teamId.toString() ||
    before.identityTeams.some((t) => t !== teamId.toString()) ||
    identities.length !== (user.gameIdentities ?? []).length;

  report.repaired.push({
    userId: uid,
    username: user.username,
    squadRole: role,
    changed: afterMismatch,
    before,
  });
}

const orphanUsers = await usersCol
  .find({
    _id: { $nin: rosterUserIds.map((id) => new mongoose.Types.ObjectId(id)) },
    $or: [{ playerTeamId: teamId }, { 'gameIdentities.playerTeamId': teamId }],
  })
  .project({ username: 1, playerTeamId: 1, gameIdentities: 1 })
  .toArray();

for (const u of orphanUsers) {
  const identities = (u.gameIdentities ?? []).map((g) => ({
    _id: g._id,
    serverNumber: g.serverNumber,
    gameNickname: g.gameNickname,
    playerTeamId: null,
  }));
  await usersCol.updateOne(
    { _id: u._id },
    {
      $set: {
        gameIdentities: identities,
        playerTeamId: null,
        teamTag: null,
        teamDisplayName: null,
      },
    },
  );
  report.clearedOrphans.push({ userId: u._id.toString(), username: u.username });
}

console.log(JSON.stringify(report, null, 2));
await mongoose.disconnect();
