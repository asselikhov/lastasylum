/**
 * One-off: set membershipStatus for legacy users; optionally promote admin to R5.
 *
 * Optional in backend/.env:
 *   SCRIPT_ADMIN_USERNAME=SomeAdmin
 *
 * Usage: node scripts/normalize-legacy-users.mjs
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
const adminUsername = env.SCRIPT_ADMIN_USERNAME?.trim();

if (!uri) {
  console.error('Missing MONGODB_URI in .env');
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const col = mongoose.connection.db.collection('users');

const legacy = await col.updateMany(
  { $or: [{ membershipStatus: { $exists: false } }, { membershipStatus: null }] },
  { $set: { membershipStatus: 'active' } },
);

const summary = {
  legacyMembership: {
    matched: legacy.matchedCount,
    modified: legacy.modifiedCount,
  },
  adminPromotion: null,
};

if (adminUsername) {
  const admin = await col.updateMany(
    { username: { $regex: new RegExp(`^${adminUsername.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}$`, 'i') } },
    { $set: { role: 'R5', membershipStatus: 'active' } },
  );
  summary.adminPromotion = {
    username: adminUsername,
    matched: admin.matchedCount,
    modified: admin.modifiedCount,
  };
}

console.log(JSON.stringify(summary, null, 2));
await mongoose.disconnect();
