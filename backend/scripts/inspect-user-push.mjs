/**
 *   node scripts/inspect-user-push.mjs "Test 1"
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const q = (process.argv[2] || 'Test 1').trim();
const env = loadBackendEnv();
await mongoose.connect(env.MONGODB_URI, { dbName: env.MONGODB_DB_NAME || 'last_asylum' });
const col = mongoose.connection.db.collection('users');
const re = new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i');
const u = await col.findOne({
  $or: [{ username: re }, { email: re }],
});
if (!u) {
  console.error('User not found:', q);
  process.exit(1);
}
console.log(
  JSON.stringify(
    {
      id: u._id.toString(),
      username: u.username,
      email: u.email,
      role: u.role,
      membershipStatus: u.membershipStatus,
      playerTeamId: u.playerTeamId?.toString() ?? null,
      pushFcmTokens: u.pushFcmTokens ?? [],
      excavationPushEnabled: u.excavationPushEnabled,
      presenceStatus: u.presenceStatus,
      lastPresenceAt: u.lastPresenceAt,
      lastAppActiveAt: u.lastAppActiveAt,
      updatedAt: u.updatedAt,
    },
    null,
    2,
  ),
);
await mongoose.disconnect();
