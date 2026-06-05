export type ParsedAndroidUserAgent = {
  versionName: string;
  versionCode: number;
};

const ANDROID_UA_RE = /^SquadRelay-Android\/([^\s]+) \((\d+)\)$/i;

export function parseAndroidUserAgent(
  userAgent: string | null | undefined,
): ParsedAndroidUserAgent | null {
  const raw = userAgent?.trim();
  if (!raw) return null;
  const match = ANDROID_UA_RE.exec(raw);
  if (!match) return null;
  const versionName = match[1]?.trim();
  const versionCode = Number.parseInt(match[2] ?? '', 10);
  if (!versionName || !Number.isFinite(versionCode) || versionCode < 0) {
    return null;
  }
  return { versionName, versionCode };
}
