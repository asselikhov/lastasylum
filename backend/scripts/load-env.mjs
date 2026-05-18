import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const scriptsDir = path.dirname(fileURLToPath(import.meta.url));

/** Load backend/.env into a plain object (no expansion). */
export function loadBackendEnv() {
  const envPath = path.join(scriptsDir, '..', '.env');
  if (!fs.existsSync(envPath)) {
    console.error(`Missing ${envPath} — copy .env.example and configure locally.`);
    process.exit(1);
  }
  const text = fs.readFileSync(envPath, 'utf8');
  const out = {};
  for (const line of text.split(/\n/)) {
    const t = line.trim();
    if (!t || t.startsWith('#')) continue;
    const i = t.indexOf('=');
    if (i === -1) continue;
    out[t.slice(0, i).trim()] = t.slice(i + 1).trim();
  }
  return out;
}
