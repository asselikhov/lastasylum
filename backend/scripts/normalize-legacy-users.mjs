/**
 * One-off: set membershipStatus for legacy users; ensure admin Liveliness is R5.
 * Reads MONGODB_URI and MONGODB_DB_NAME from backend/.env (not committed).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import mongoose from 'mongoose';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

function loadEnvFile(filePath) {
  const text = fs.readFileSync(filePath, 'utf8');
  const out = {};
  for (const line of text.split(/\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i === -1) continue;
    const key = t.slice(0, i).trim();
    out[key] = t.slice(i + 1).trim();
  }
  return out;
}

const envPath = path.join(__dirname, '..', '.env');
const env = loadEnvFile(envPath);
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';

if (!uri) {
  console.error('Missing MONGODB_URI in backend/.env');
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const col = mongoose.connection.db.collection('users');

const legacy = await col.updateMany(
  { $or: [{ membershipStatus: { $exists: false } }, { membershipStatus: null }] },
  { $set: { membershipStatus: 'active' } },
);

const admin = await col.updateMany(
  { username: { $regex: /^liveliness$/i } },
  { $set: { role: 'R5', membershipStatus: 'active' } },
);

console.log(
  JSON.stringify(
    {
      legacyMembership: { matched: legacy.matchedCount, modified: legacy.modifiedCount },
      livelinessAdmin: { matched: admin.matchedCount, modified: admin.modifiedCount },
    },
    null,
    2,
  ),
);

await mongoose.disconnect();
