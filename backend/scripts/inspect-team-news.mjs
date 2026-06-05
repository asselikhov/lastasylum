/**
 * Inspect team_news collection. Usage:
 *   node scripts/inspect-team-news.mjs [teamId]
 */
import mongoose from 'mongoose';
import { loadBackendEnv } from './load-env.mjs';

const teamIdArg = (process.argv[2] || '').trim();
const env = loadBackendEnv();
const uri = env.MONGODB_URI;
const dbName = env.MONGODB_DB_NAME || 'last_asylum';
if (!uri) {
  console.error('Missing MONGODB_URI in .env');
  process.exit(1);
}

await mongoose.connect(uri, { dbName });
const db = mongoose.connection.db;
const col = db.collection('team_news');

const teamRelated = {};
for (const c of await db.listCollections().toArray()) {
  const name = c.name;
  if (name.includes('team') || name.includes('news')) {
    teamRelated[name] = await db.collection(name).countDocuments();
  }
}

const filter = teamIdArg && mongoose.Types.ObjectId.isValid(teamIdArg)
  ? { teamId: new mongoose.Types.ObjectId(teamIdArg) }
  : {};

const total = await col.countDocuments(filter);
const withPoll = await col.countDocuments({ ...filter, poll: { $ne: null } });
const recent = await col
  .find(filter)
  .sort({ _id: -1 })
  .limit(30)
  .project({ teamId: 1, title: 1, authorUserId: 1, poll: 1, createdAt: 1, updatedAt: 1 })
  .toArray();

const byTeam = await col
  .aggregate([
    ...(teamIdArg ? [{ $match: filter }] : []),
    {
      $group: {
        _id: '$teamId',
        count: { $sum: 1 },
        pollCount: { $sum: { $cond: [{ $ne: ['$poll', null] }, 1, 0] } },
      },
    },
    { $sort: { count: -1 } },
    { $limit: 20 },
  ])
  .toArray();

console.log(
  JSON.stringify(
    {
      dbName,
      filterTeamId: teamIdArg || null,
      teamRelatedCollections: teamRelated,
      total,
      withPoll,
      byTeam: byTeam.map((t) => ({
        teamId: t._id?.toString(),
        count: t.count,
        pollCount: t.pollCount,
      })),
      recent: recent.map((r) => ({
        id: r._id?.toString(),
        teamId: r.teamId?.toString(),
        title: r.title?.slice(0, 120),
        hasPoll: Boolean(r.poll),
        pollQuestion: r.poll?.question?.slice(0, 120) ?? null,
        authorUserId: r.authorUserId,
        createdAt: r.createdAt,
        updatedAt: r.updatedAt,
      })),
    },
    null,
    2,
  ),
);

const readStates = await db
  .collection('teamnewsreadstates')
  .find({})
  .limit(20)
  .toArray();
const attachments = await db
  .collection('team_news_attachments')
  .find({})
  .sort({ _id: -1 })
  .limit(5)
  .toArray();
const teams = await db.collection('playerteams').find({}).project({ name: 1, tag: 1 }).toArray();

console.log(
  JSON.stringify(
    {
      playerTeams: teams.map((t) => ({
        id: t._id?.toString(),
        name: t.name,
        tag: t.tag,
      })),
      readStates: readStates.map((r) => ({
        teamId: r.teamId?.toString(),
        userId: r.userId,
        lastSeenCreatedAt: r.lastSeenCreatedAt,
      })),
      sampleAttachments: attachments.map((a) => ({
        id: a._id?.toString(),
        teamId: a.teamId?.toString(),
        uploadedByUserId: a.uploadedByUserId,
        createdAt: a.createdAt,
      })),
    },
    null,
    2,
  ),
);

await mongoose.disconnect();
