/**
 * Verifies FIREBASE_SERVICE_ACCOUNT_JSON in backend/.env (parses + Firebase Admin init).
 * Does not send a push unless TEST_FCM_TOKEN is set in .env.
 *
 *   node scripts/test-fcm-config.mjs
 */
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import admin from 'firebase-admin';
import { loadBackendEnv } from './load-env.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const backendDir = join(__dirname, '..');

const env = loadBackendEnv();
for (const [k, v] of Object.entries(env)) {
  if (process.env[k] == null || process.env[k] === '') process.env[k] = v;
}

function parseFirebaseServiceAccountJson(raw) {
  const trimmed = raw.trim();
  const attempts = [trimmed];
  if (
    (trimmed.startsWith("'") && trimmed.endsWith("'")) ||
    (trimmed.startsWith('"') && trimmed.endsWith('"'))
  ) {
    attempts.push(trimmed.slice(1, -1));
  }
  attempts.push(trimmed.replace(/\r?\n/g, '\\n'));
  let lastError;
  for (const candidate of attempts) {
    try {
      const parsed = JSON.parse(candidate);
      if (
        typeof parsed.private_key === 'string' &&
        parsed.private_key.includes('\\n')
      ) {
        parsed.private_key = parsed.private_key.replace(/\\n/g, '\n');
      }
      return parsed;
    } catch (e) {
      lastError = e;
    }
  }
  throw lastError ?? new Error('invalid FIREBASE_SERVICE_ACCOUNT_JSON');
}

const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON?.trim();
if (!raw) {
  console.error('FIREBASE_SERVICE_ACCOUNT_JSON is not set in backend/.env');
  process.exit(1);
}

let cred;
try {
  cred = parseFirebaseServiceAccountJson(raw);
} catch (e) {
  console.error('FIREBASE_SERVICE_ACCOUNT_JSON is not valid JSON:', e.message);
  process.exit(1);
}

if (!cred.project_id || !cred.private_key || !cred.client_email) {
  console.error('Service account JSON missing project_id, private_key, or client_email');
  process.exit(1);
}

if (!admin.apps.length) {
  admin.initializeApp({ credential: admin.credential.cert(cred) });
}
console.log('OK: Firebase Admin initialized for project', cred.project_id);

const testToken = process.env.TEST_FCM_TOKEN?.trim();
if (testToken) {
  const res = await admin.messaging().send({
    token: testToken,
    notification: { title: 'SquadRelay test', body: 'FCM config works' },
    data: { type: 'excavation_alert', roomId: 'test' },
    android: { priority: 'high', notification: { channelId: 'excavation_alerts' } },
  });
  console.log('OK: test message sent, id=', res);
} else {
  console.log(
    'Tip: set TEST_FCM_TOKEN in .env to a device token from Mongo pushFcmTokens, then re-run.',
  );
}
