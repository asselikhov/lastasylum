/**
 * Send a test message using SMTP_* from backend/.env
 *
 * Usage:
 *   node scripts/test-smtp.mjs you@example.com
 */
import nodemailer from 'nodemailer';
import { loadBackendEnv } from './load-env.mjs';

const to = (process.argv[2] || '').trim().toLowerCase();
if (!to || !to.includes('@')) {
  console.error('Usage: node scripts/test-smtp.mjs <recipient-email>');
  process.exit(1);
}

const env = loadBackendEnv();
const host = env.SMTP_HOST?.trim();
if (!host) {
  console.error('SMTP_HOST is empty in backend/.env — run scripts/setup-smtp.ps1 first.');
  process.exit(1);
}

const port = Number(env.SMTP_PORT) || 587;
const secure = env.SMTP_SECURE === 'true';
const user = env.SMTP_USER?.trim();
const pass = env.SMTP_PASS;
const from = env.SMTP_FROM?.trim() || 'noreply@localhost';
const appName = env.APP_PUBLIC_NAME?.trim() || 'SquadRelay';

const transport = nodemailer.createTransport({
  host,
  port,
  secure,
  auth: user && pass !== undefined && pass !== '' ? { user, pass } : undefined,
});

const token = 'test-' + Date.now().toString(36);

try {
  const info = await transport.sendMail({
    from,
    to,
    subject: `${appName}: тест SMTP`,
    text: `Если вы видите это письмо, SMTP настроен.\n\nТестовый токен: ${token}\n`,
    html: `<p>Если вы видите это письмо, <strong>SMTP настроен</strong>.</p><p>Тестовый токен: <code>${token}</code></p>`,
  });
  console.log(JSON.stringify({ ok: true, messageId: info.messageId, to }, null, 2));
} catch (err) {
  console.error(
    JSON.stringify(
      {
        ok: false,
        error: err instanceof Error ? err.message : String(err),
        hint: 'Check SMTP_HOST/PORT/USER/PASS and sender verification at your provider.',
      },
      null,
      2,
    ),
  );
  process.exit(1);
}
