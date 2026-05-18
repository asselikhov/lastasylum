/**
 * Reads mobile-android/app/google-services.json → repo local.properties (Android).
 * If backend/firebase-service-account.json exists → backend/.env FIREBASE_SERVICE_ACCOUNT_JSON.
 *
 *   node scripts/apply-firebase-from-google-services.mjs
 */
import { readFileSync, writeFileSync, existsSync, copyFileSync, readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const backendDir = join(__dirname, '..');
const repoRoot = join(backendDir, '..');
const googleServicesPath = join(repoRoot, 'mobile-android', 'app', 'google-services.json');
const serviceAccountPath = join(backendDir, 'firebase-service-account.json');
const envPath = join(backendDir, '.env');
const localPropsPaths = [
  join(repoRoot, 'local.properties'),
  join(repoRoot, 'mobile-android', 'local.properties'),
];

function parseGoogleServices(path) {
  const root = JSON.parse(readFileSync(path, 'utf8'));
  const projectId = root.project_info?.project_id?.trim() ?? '';
  const clients = root.client ?? [];
  const android = clients.find(
    (c) => c.client_info?.android_client_info?.package_name === 'com.lastasylum.alliance',
  );
  const appId = android?.client_info?.mobilesdk_app_id?.trim() ?? '';
  const apiKey = android?.api_key?.[0]?.current_key?.trim() ?? '';
  return { projectId, appId, apiKey };
}

function upsertPropertiesFile(filePath, entries) {
  const lines = existsSync(filePath)
    ? readFileSync(filePath, 'utf8').split(/\r?\n/)
    : [];
  const keys = new Set(entries.map(([k]) => k));
  const kept = lines.filter((line) => {
    const m = line.match(/^\s*([^#=]+?)\s*=/);
    return !m || !keys.has(m[1].trim());
  });
  const block = entries.map(([k, v]) => `${k}=${v}`);
  const out = [...kept.filter((l, i, a) => !(i === a.length - 1 && l === ''))];
  if (out.length) out.push('');
  out.push('# Firebase (auto from google-services.json)');
  out.push(...block);
  writeFileSync(filePath, out.join('\n') + '\n', 'utf8');
}

function setEnvKey(envFile, key, value) {
  const lines = existsSync(envFile)
    ? readFileSync(envFile, 'utf8').split(/\r?\n/)
    : [];
  const escaped = key.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const filtered = lines.filter((line) => !new RegExp(`^\\s*${escaped}\\s*=`).test(line));
  filtered.push(`${key}=${value}`);
  writeFileSync(envFile, filtered.join('\n') + '\n', 'utf8');
}

if (!existsSync(googleServicesPath)) {
  console.error('Missing:', googleServicesPath);
  console.error('Download google-services.json from Firebase Console into that path.');
  process.exit(1);
}

const { projectId, appId, apiKey } = parseGoogleServices(googleServicesPath);
if (!projectId || !appId || !apiKey) {
  console.error('Could not parse project_id / app id / api_key from google-services.json');
  process.exit(1);
}

const firebaseEntries = [
  ['squadrelay.firebase.projectId', projectId],
  ['squadrelay.firebase.appId', appId],
  ['squadrelay.firebase.apiKey', apiKey],
];

for (const p of localPropsPaths) {
  upsertPropertiesFile(p, firebaseEntries);
  console.log('Updated', p);
}

if (!existsSync(envPath) && existsSync(join(backendDir, '.env.example'))) {
  copyFileSync(join(backendDir, '.env.example'), envPath);
  console.log('Created backend/.env from .env.example');
}

function resolveServiceAccountPath() {
  if (existsSync(serviceAccountPath)) return serviceAccountPath;
  const mobileDir = join(repoRoot, 'mobile-android');
  if (!existsSync(mobileDir)) return null;
  const hit = readdirSync(mobileDir).find(
    (name) => name.includes('firebase-adminsdk') && name.endsWith('.json'),
  );
  if (!hit) return null;
  const src = join(mobileDir, hit);
  copyFileSync(src, serviceAccountPath);
  console.log('Copied', src, '→', serviceAccountPath);
  return serviceAccountPath;
}

let renderReady = false;
const saPath = resolveServiceAccountPath();
if (saPath) {
  const oneLine = JSON.stringify(JSON.parse(readFileSync(saPath, 'utf8')));
  setEnvKey(envPath, 'FIREBASE_SERVICE_ACCOUNT_JSON', oneLine);
  console.log('Updated backend/.env → FIREBASE_SERVICE_ACCOUNT_JSON');
  renderReady = true;
} else {
  console.log('');
  console.log('Render (one manual step):');
  console.log('  1. Firebase Console → Project settings → Service accounts');
  console.log('  2. Generate new private key → save as:');
  console.log('     backend/firebase-service-account.json');
  console.log('  3. Re-run: node scripts/apply-firebase-from-google-services.mjs');
  console.log('  4. Copy FIREBASE_SERVICE_ACCOUNT_JSON from backend/.env to Render → Environment');
}

console.log('');
console.log('Android Firebase config:');
console.log('  projectId:', projectId);
console.log('  appId:    ', appId);
console.log('  apiKey:   ', apiKey.slice(0, 8) + '…');
console.log('');
console.log(renderReady ? 'Backend FCM: ready locally. Deploy .env to Render.' : 'Backend FCM: waiting for firebase-service-account.json');
