# Free tier Render + MongoDB

On Render free tier and shared MongoDB:

- **Cold start**: after ~15 min idle the API may take 30–90s to wake; first chat request is slow — not an app bug.
- **Shared CPU**: under load, `POST/GET /chat/messages` can spike; upgrade instance or Mongo tier when the team grows.
- **Indexes**: after deploy run `db.messages.getIndexes()` and confirm `{ roomId: 1, deletedAt: 1, senderId: 1, _id: 1 }` exists.
- **Logs**: `SlowRequest` warns when chat endpoints exceed 500ms or other routes exceed 2s.
