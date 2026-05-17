/**
 * One-off: keep single team ОБЖОРЫ (OBZH), unify chat routing for RustlingGrass, DrOwljae, Kseo.
 * - Sync allianceName to OBZHORY for all team members
 * - Clear playerTeamId from everyone else; delete other teams (if any)
 * - Remove duplicate SquadRelay hub/raid rooms (keep __global__ and OBZHORY team rooms)
 * - Ensure pt:<teamId> raid/hub rooms exist
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import mongoose from 'mongoose';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const TEAM_TAG = 'OBZH';
const ALLIANCE_SCOPE = 'OBZHORY';
const MEMBER_USERNAMES = ['RustlingGrass', 'DrOwljae', 'Kseo'];
const GLOBAL = '__global__';
const DEFAULT_ALLIANCE = 'SquadRelay';

function loadEnvFile(filePath) {
  const text = fs.readFileSync(filePath, 'utf8');
  const out = {};
  for (const line of text.split(/\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i === -1) continue;
    out[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return out;
}

const env = loadEnvFile(path.join(__dirname, '..', '.env'));
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
if (!uri) {
  console.error('Missing MONGODB_URI');
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
  (await teamsCol.findOne({ displayName: /ОБЖОР/i }));

if (!keepTeam) {
  console.error('Team OBZH/ОБЖОРЫ not found');
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
const memberIds = memberDocs.map((u) => u._id);

// Delete all other player teams
const deletedTeams = await teamsCol.deleteMany({ _id: { $ne: teamId } });

// Rebuild squad roster: leader R5, others keep existing squad role or R1
const squadMembers = [];
for (const u of memberDocs) {
  const existing = (keepTeam.squadMembers || []).find((m) =>
    m.userId?.equals(u._id),
  );
  let role = existing?.role || 'R1';
  if (u._id.equals(leaderId)) role = 'R5';
  squadMembers.push({ userId: u._id, role });
}

await teamsCol.updateOne(
  { _id: teamId },
  {
    $set: {
      tag: TEAM_TAG,
      displayName: keepTeam.displayName || 'ОБЖОРЫ',
      leaderUserId: leaderId,
      squadMembers,
    },
    $unset: { memberUserIds: '' },
  },
);

// Clear team from all other users
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

// Assign OBZHORY team to the three members
for (const u of memberDocs) {
  await usersCol.updateOne(
    { _id: u._id },
    {
      $set: {
        playerTeamId: teamId,
        teamDisplayName: keepTeam.displayName || 'ОБЖОРЫ',
        teamTag: TEAM_TAG,
        allianceName: ALLIANCE_SCOPE,
        membershipStatus: 'active',
      },
    },
  );
}

// Reject/clear pending join requests for deleted teams
const joinCleanup = await joinCol.deleteMany({ teamId: { $ne: teamId } });

// Archive duplicate SquadRelay hub/raid (not the legacy «Общий»)
const squadRelayDupes = await roomsCol.updateMany(
  {
    allianceId: DEFAULT_ALLIANCE,
    title: { $in: ['ОБЖОРЫ', 'Рейд'] },
    archivedAt: null,
  },
  { $set: { archivedAt: new Date() } },
);

// Ensure OBZHORY hub + raid
const now = new Date();
let hub = await roomsCol.findOne({
  allianceId: ALLIANCE_SCOPE,
  sortOrder: 1,
  archivedAt: null,
});
if (!hub) {
  await roomsCol.insertOne({
    allianceId: ALLIANCE_SCOPE,
    title: 'ОБЖОРЫ',
    sortOrder: 1,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
  });
} else if (hub.title !== 'ОБЖОРЫ') {
  await roomsCol.updateOne({ _id: hub._id }, { $set: { title: 'ОБЖОРЫ' } });
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

// pt: scope rooms for new backend routing
let ptHub = await roomsCol.findOne({ allianceId: ptScope, sortOrder: 1, archivedAt: null });
if (!ptHub) {
  await roomsCol.insertOne({
    allianceId: ptScope,
    title: 'ОБЖОРЫ',
    sortOrder: 1,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
  });
}
let ptRaid = await roomsCol.findOne({ allianceId: ptScope, title: 'Рейд', archivedAt: null });
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
