import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const env = loadBackendEnv();
await mongoose.connect(env.MONGODB_URI, { dbName: env.MONGODB_DB_NAME || 'last_asylum' });
const msgs = await mongoose.connection.db
  .collection('messages')
  .find({ text: /раскопк/i })
  .sort({ createdAt: -1 })
  .limit(5)
  .project({ text: 1, allianceId: 1, authorId: 1, createdAt: 1, roomId: 1 })
  .toArray();
console.log(JSON.stringify(msgs, null, 2));
await mongoose.disconnect();
