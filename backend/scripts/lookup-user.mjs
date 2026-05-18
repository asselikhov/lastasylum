/**
 * Lookup users by username/email fragment. Usage:
 *   node scripts/lookup-user.mjs DrOwljae
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const query = (process.argv[2] || '').trim();
if (!query) {
  console.error('Usage: node scripts/lookup-user.mjs <username-or-email-fragment>');
  process.exit(1);
}

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
if (!uri) {
  console.error('Missing MONGODB_URI in .env');
  process.exit(1);
}

const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const re = new RegExp(escaped, 'i');

await mongoose.connect(uri, { dbName });
const col = mongoose.connection.db.collection('users');

const users = await col
  .find({
    $or: [{ username: re }, { email: re }],
  })
  .project({
    username: 1,
    email: 1,
    membershipStatus: 1,
    role: 1,
    passwordResetExpires: 1,
    createdAt: 1,
    updatedAt: 1,
  })
  .limit(10)
  .toArray();

console.log(
  JSON.stringify(
    {
      query,
      smtpConfigured: Boolean(env.SMTP_HOST?.trim()),
      smtpFrom: env.SMTP_FROM?.trim() || null,
      count: users.length,
      users: users.map((u) => ({
        id: u._id?.toString(),
        username: u.username,
        email: u.email,
        membershipStatus: u.membershipStatus ?? '(missing→active)',
        role: u.role,
        hasPendingReset: Boolean(
          u.passwordResetExpires && new Date(u.passwordResetExpires) > new Date(),
        ),
        createdAt: u.createdAt,
      })),
    },
    null,
    2,
  ),
);

await mongoose.disconnect();
