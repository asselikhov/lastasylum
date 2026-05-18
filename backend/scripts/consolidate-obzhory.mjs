/**
 * One-off DB maintenance: keep one player team, sync members, chat room scopes.
 *
 * Configure via backend/.env (do not commit real usernames to git):
 *   SCRIPT_TEAM_TAG=OBZH
 *   SCRIPT_ALLIANCE_SCOPE=OBZHORY
 *   SCRIPT_MEMBER_USERNAMES=user1,user2,user3
 *
 * Usage: node scripts/consolidate-obzhory.mjs
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const GLOBAL = '__global__';
const DEFAULT_ALLIANCE = 'SquadRelay';

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';

const TEAM_TAG = env.SCRIPT_TEAM_TAG?.trim();
const ALLIANCE_SCOPE = env.SCRIPT_ALLIANCE_SCOPE?.trim();
const MEMBER_USERNAMES = (env.SCRIPT_MEMBER_USERNAMES ?? '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

if (!uri) {
  console.error('Missing MONGODB_URI in .env');
  process.exit(1);
}
if (!TEAM_TAG || !ALLIANCE_SCOPE || MEMBER_USERNAMES.length === 0) {
  console.error(
    'Set SCRIPT_TEAM_TAG, SCRIPT_ALLIANCE_SCOPE, and SCRIPT_MEMBER_USERNAMES in backend/.env',
  );
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const db = mongoose.connection.db;
const teamsCol = db.collection('playerteams');
const usersCol = db.collection('users');
const roomsCol = db.collection('chatrooms');
const joinCol = db.collection('teamjoinrequests');

const keepTeam =
  (await teamsCol.findOne({ tag: TEAM_TAG })) ||
  (await teamsCol.findOne({ displayName: new RegExp(TEAM_TAG, 'i') }));

if (!keepTeam) {
  console.error(`Team with tag "${TEAM_TAG}" not found`);
  process.exit(1);
}

const teamId = keepTeam._id;
const ptScope = `pt:${teamId.toString()}`;

const memberDocs = await usersCol
  .find({ username: { $in: MEMBER_USERNAMES } })
  .toArray();

if (memberDocs.length !== MEMBER_USERNAMES.length) {
  const found = memberDocs.map((u) => u.username);
  const missing = MEMBER_USERNAMES.filter((n) => !found.includes(n));
  console.error('Missing users:', missing);
  process.exit(1);
}

const leaderId = keepTeam.leaderUserId;
const squadMembers = [];
for (const u of memberDocs) {
  const existing = (keepTeam.squadMembers || []).find((m) =>
    m.userId?.equals(u._id),
  );
  let role = existing?.role || 'R1';
  if (u._id.equals(leaderId)) role = 'R5';
  squadMembers.push({ userId: u._id, role });
}

const deletedTeams = await teamsCol.deleteMany({ _id: { $ne: teamId } });

await teamsCol.updateOne(
  { _id: teamId },
  {
    $set: {
      tag: TEAM_TAG,
      displayName: keepTeam.displayName || TEAM_TAG,
      leaderUserId: leaderId,
      squadMembers,
    },
    $unset: { memberUserIds: '' },
  },
);

const clearedOthers = await usersCol.updateMany(
  {
    $and: [
      { playerTeamId: { $ne: null } },
      { playerTeamId: { $ne: teamId } },
    ],
  },
  {
    $set: {
      playerTeamId: null,
      teamDisplayName: null,
      teamTag: null,
    },
  },
);

for (const u of memberDocs) {
  await usersCol.updateOne(
    { _id: u._id },
    {
      $set: {
        playerTeamId: teamId,
        teamDisplayName: keepTeam.displayName || TEAM_TAG,
        teamTag: TEAM_TAG,
        allianceName: ALLIANCE_SCOPE,
        membershipStatus: 'active',
      },
    },
  );
}

const joinCleanup = await joinCol.deleteMany({ teamId: { $ne: teamId } });

const squadRelayDupes = await roomsCol.updateMany(
  {
    allianceId: DEFAULT_ALLIANCE,
    title: { $in: ['ОБЖОРЫ', 'Рейд'] },
    archivedAt: null,
  },
  { $set: { archivedAt: new Date() } },
);

const now = new Date();
const hubTitle = keepTeam.displayName || TEAM_TAG;

let hub = await roomsCol.findOne({
  allianceId: ALLIANCE_SCOPE,
  sortOrder: 1,
  archivedAt: null,
});
if (!hub) {
  await roomsCol.insertOne({
    allianceId: ALLIANCE_SCOPE,
    title: hubTitle,
    sortOrder: 1,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
  });
} else if (hub.title !== hubTitle) {
  await roomsCol.updateOne({ _id: hub._id }, { $set: { title: hubTitle } });
}

let raid = await roomsCol.findOne({
  allianceId: ALLIANCE_SCOPE,
  title: 'Рейд',
  archivedAt: null,
});
if (!raid) {
  await roomsCol.insertOne({
    allianceId: ALLIANCE_SCOPE,
    title: 'Рейд',
    sortOrder: 2,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
  });
}

let ptHub = await roomsCol.findOne({
  allianceId: ptScope,
  sortOrder: 1,
  archivedAt: null,
});
if (!ptHub) {
  await roomsCol.insertOne({
    allianceId: ptScope,
    title: hubTitle,
    sortOrder: 1,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
  });
}
let ptRaid = await roomsCol.findOne({
  allianceId: ptScope,
  title: 'Рейд',
  archivedAt: null,
});
if (!ptRaid) {
  await roomsCol.insertOne({
    allianceId: ptScope,
    title: 'Рейд',
    sortOrder: 2,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
  });
}

const finalTeams = await teamsCol.countDocuments({});
const finalRoster = await usersCol
  .find({ playerTeamId: teamId })
  .project({ username: 1, allianceName: 1, teamTag: 1 })
  .toArray();

console.log(
  JSON.stringify(
    {
      keptTeam: { id: teamId.toString(), tag: TEAM_TAG, displayName: keepTeam.displayName },
      deletedOtherTeams: deletedTeams.deletedCount,
      clearedOtherUsers: clearedOthers.modifiedCount,
      joinRequestsRemoved: joinCleanup.deletedCount,
      archivedSquadRelayDupRooms: squadRelayDupes.modifiedCount,
      ptScope,
      teamCountAfter: finalTeams,
      roster: finalRoster,
      activeRoomScopes: await roomsCol.distinct('allianceId', { archivedAt: null }),
    },
    null,
    2,
  ),
);

await mongoose.disconnect();
