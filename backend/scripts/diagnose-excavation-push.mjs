/**
 * Diagnose why excavation FCM may not reach teammates.
 *   node scripts/diagnose-excavation-push.mjs
 *   node scripts/diagnose-excavation-push.mjs <senderUserId>
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const STALE_MS = 90_000;
const senderId = (process.argv[2] || '').trim();

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
if (!uri) {
  console.error('Missing MONGODB_URI in backend/.env');
  process.exit(1);
}

const firebaseConfigured = Boolean(env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim());
let firebaseProject = null;
if (firebaseConfigured) {
  try {
    firebaseProject = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON).project_id;
  } catch {
    firebaseProject = '(invalid JSON in .env)';
  }
}

await mongoose.connect(uri, { dbName });
const users = mongoose.connection.db.collection('users');

const all = await users
  .find({ membershipStatus: 'active' })
  .project({
    username: 1,
    playerTeamId: 1,
    allianceName: 1,
    pushFcmTokens: 1,
    excavationPushEnabled: 1,
    presenceStatus: 1,
    lastPresenceAt: 1,
  })
  .toArray();

const staleBefore = new Date(Date.now() - STALE_MS);

function wouldReceiveExcavationPush(u, excludeId) {
  if (u._id.toString() === excludeId) return { ok: false, why: 'sender' };
  const tokens = u.pushFcmTokens;
  if (!Array.isArray(tokens) || tokens.length === 0) {
    return { ok: false, why: 'no pushFcmTokens' };
  }
  if (u.excavationPushEnabled === false) {
    return { ok: false, why: 'excavationPushEnabled=false' };
  }
  const ingame = (u.presenceStatus || '').toLowerCase() === 'ingame';
  const lastAt = u.lastPresenceAt ? new Date(u.lastPresenceAt) : null;
  const freshIngame =
    ingame && lastAt && !Number.isNaN(lastAt.getTime()) && lastAt >= staleBefore;
  if (freshIngame) {
    return {
      ok: false,
      why: `ingame (lastPresenceAt ${lastAt.toISOString()})`,
    };
  }
  return { ok: true, why: 'eligible', tokenCount: tokens.length };
}

function teamScope(u) {
  if (u.playerTeamId) return `pt:${u.playerTeamId.toString()}`;
  return u.allianceName || '(no team)';
}

const byTeam = new Map();
for (const u of all) {
  const scope = teamScope(u);
  if (!byTeam.has(scope)) byTeam.set(scope, []);
  byTeam.get(scope).push(u);
}

const report = {
  localEnv: {
    firebaseConfigured,
    firebaseProject,
  },
  activeUsers: all.length,
  teams: [],
};

for (const [scope, members] of byTeam) {
  const exclude = senderId || members[0]?._id?.toString() || '';
  const rows = members.map((u) => {
    const r = wouldReceiveExcavationPush(u, exclude);
    return {
      id: u._id.toString(),
      username: u.username,
      tokens: Array.isArray(u.pushFcmTokens) ? u.pushFcmTokens.length : 0,
      excavationPushEnabled: u.excavationPushEnabled !== false,
      presenceStatus: u.presenceStatus ?? null,
      lastPresenceAt: u.lastPresenceAt
        ? new Date(u.lastPresenceAt).toISOString()
        : null,
      excavationPush: r,
    };
  });
  report.teams.push({
    allianceScope: scope,
    memberCount: members.length,
    simulateExcludeUserId: exclude || '(first member)',
    eligibleTokenCount: rows
      .filter((r) => r.excavationPush.ok)
      .reduce((s, r) => s + r.tokens, 0),
    members: rows,
  });
}

console.log(JSON.stringify(report, null, 2));

await mongoose.disconnect();
