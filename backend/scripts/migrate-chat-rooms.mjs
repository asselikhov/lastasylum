/**
 * One-off: create default "Общий" chat room per alliance and set Message.roomId.
 * Usage: node scripts/migrate-chat-rooms.mjs
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';

if (!uri) {
  console.error('Missing MONGODB_URI in .env');
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const db = mongoose.connection.db;
const messages = db.collection('messages');
const chatrooms = db.collection('chatrooms');

const messageAlliances = await messages.distinct('allianceId');
const users = db.collection('users');
const userAlliances = await users.distinct('allianceName');
const allAllianceIds = [...new Set([...messageAlliances, ...userAlliances])];

const summary = { alliances: 0, roomsCreated: 0, messagesUpdated: 0 };

for (const allianceId of allAllianceIds) {
  if (!allianceId) continue;
  summary.alliances += 1;
  let room = await chatrooms.findOne({
    allianceId,
    title: 'Общий',
    archivedAt: null,
  });
  if (!room) {
    const now = new Date();
    const ins = await chatrooms.insertOne({
      allianceId,
      title: 'Общий',
      sortOrder: 0,
      archivedAt: null,
      createdAt: now,
      updatedAt: now,
    });
    room = { _id: ins.insertedId };
    summary.roomsCreated += 1;
  }
  const res = await messages.updateMany(
    {
      allianceId,
      $or: [{ roomId: { $exists: false } }, { roomId: null }],
    },
    { $set: { roomId: room._id } },
  );
  summary.messagesUpdated += res.modifiedCount ?? 0;
}

console.log(JSON.stringify(summary, null, 2));
await mongoose.disconnect();
