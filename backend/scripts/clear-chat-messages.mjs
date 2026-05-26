/**
 * Delete all chat messages (and read cursors / attachment metadata) from MongoDB.
 * Chat rooms are kept; only message history is wiped.
 *
 * Prerequisites: backup MongoDB first.
 *
 * In backend/.env set:
 *   SCRIPT_CONFIRM_CLEAR_CHAT=yes
 *
 * Optional:
 *   SCRIPT_DRY_RUN=1   — print counts only, no deletes
 *
 * Usage (from backend/):
 *   node scripts/clear-chat-messages.mjs
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
const confirmed =
  env.SCRIPT_CONFIRM_CLEAR_CHAT === 'yes' ||
  process.env.SCRIPT_CONFIRM_CLEAR_CHAT === 'yes';
const dryRun =
  env.SCRIPT_DRY_RUN === '1' ||
  env.SCRIPT_DRY_RUN === 'true' ||
  process.env.SCRIPT_DRY_RUN === '1' ||
  process.env.SCRIPT_DRY_RUN === 'true';

if (!uri) {
  console.error('Missing MONGODB_URI in .env');
  process.exit(1);
}
if (!confirmed) {
  console.error(
    'Refusing to run: set SCRIPT_CONFIRM_CLEAR_CHAT=yes in backend/.env',
  );
  console.error('Take a MongoDB backup before clearing chat history.');
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const db = mongoose.connection.db;

const collections = [
  { name: 'messages', label: 'chat messages' },
  { name: 'chatroomreadstates', label: 'read cursors' },
  { name: 'chatattachments', label: 'attachment metadata' },
];

const counts = {};
for (const { name } of collections) {
  const col = db.collection(name);
  counts[name] = await col.countDocuments();
}

console.log(`Database: ${dbName}`);
console.log('Counts before clear:');
for (const { name, label } of collections) {
  console.log(`  ${label} (${name}): ${counts[name]}`);
}

if (dryRun) {
  console.log('\nSCRIPT_DRY_RUN set — no documents deleted.');
  await mongoose.disconnect();
  process.exit(0);
}

const deleted = {};
for (const { name, label } of collections) {
  const res = await db.collection(name).deleteMany({});
  deleted[name] = res.deletedCount ?? 0;
  console.log(`Deleted ${deleted[name]} from ${label} (${name})`);
}

console.log('\nDone. Chat rooms unchanged. R2/binary files are not removed by this script.');
await mongoose.disconnect();
